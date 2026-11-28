package com.mentis.hrms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * REQ-6-05 — the document-deadline warning/final flags on `employees` ARE driven by a scheduled job,
 * and each send is RECORDED: employees.deadline_warning_sent / deadline_final_sent flip to TRUE in the
 * same transaction as the outbox row is written (email) + in-app notification inserted.
 * Column names verified against Employee.java (deadline_warning_sent/deadline_final_sent, document_deadline DATETIME).
 * Requires @EnableScheduling on the application class (see README / 03_RUN_AND_VERIFY).
 */
@Service
public class DocumentDeadlineReminderJob {

    private static final Logger logger = LoggerFactory.getLogger(DocumentDeadlineReminderJob.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HrmsNotificationService notificationService;

    @Value("${hrms.deadline.enabled:true}")
    private boolean enabled;

    @Value("${hrms.deadline.warning-days:3}")
    private int warningDays;

    @Value("${hrms.deadline.final-days:1}")
    private int finalDays;

    @Scheduled(cron = "${hrms.deadline.cron:0 0 8 * * *}")
    @Transactional
    public void run() {
        if (!enabled) {
            logger.info("DocumentDeadlineReminderJob disabled");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int warned = sendStage(now, warningDays, "deadline_warning_sent");
        int finalled = sendStage(now, finalDays, "deadline_final_sent");
        logger.info("DocumentDeadlineReminderJob: {} warning, {} final reminder(s) sent", warned, finalled);
    }

    private int sendStage(LocalDateTime now, int days, String flagColumn) {
        LocalDateTime horizon = now.plusDays(days);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT employee_id, COALESCE(NULLIF(employee_name, ''), TRIM(CONCAT_WS(' ', first_name, last_name))) AS name, email, document_deadline " +
                "FROM employees " +
                "WHERE document_deadline IS NOT NULL AND document_deadline > ? AND document_deadline <= ? AND " + flagColumn + " = FALSE " +
                "  AND (is_active = TRUE OR is_active IS NULL AND active = TRUE) " +
                "LIMIT 500", now, horizon);
        for (Map<String, Object> row : rows) {
            String employeeId = String.valueOf(row.get("employee_id"));
            String email = row.get("email") == null ? null : String.valueOf(row.get("email"));
            Object dl = row.get("document_deadline");
            String when = dl instanceof LocalDateTime dt ? dt.format(FMT) : String.valueOf(dl);
            try {
                notificationService.notifyWithRecipient(employeeId, "ONBOARDING_DEADLINE",
                        Map.of("when", when, "employee", employeeId),
                        "/onboarding/documents",
                        "DOCDEADLINE-" + employeeId + "-" + when.replace(' ', 'T') + "-" + flagColumn,
                        email);
                jdbcTemplate.update("UPDATE employees SET " + flagColumn + " = TRUE WHERE employee_id = ?", employeeId);
            } catch (Exception e) {
                logger.error("Deadline reminder failed for {}: {}", employeeId, e.getMessage());
                throw e; // keep transaction honest: no flag flip without a recorded send
            }
        }
        return rows.size();
    }
}
