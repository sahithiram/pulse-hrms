package com.mentis.hrms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REQ-6-04 — daily digest to employees whose metrics for TODAY are INCOMPLETE (no check-out).
 * Reads employee_daily_metrics (Sprint 5 table). One notification per employee per day (eventId dedupe in outbox).
 * Requires @EnableScheduling on the application class.
 */
@Service
public class MissingCheckoutDigestJob {

    private static final Logger logger = LoggerFactory.getLogger(MissingCheckoutDigestJob.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private HrmsNotificationService notificationService;

    @Value("${hrms.checkout-digest.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${hrms.checkout-digest.cron:0 30 22 * * *}")
    @Transactional
    public void run() {
        if (!enabled) return;
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT m.employee_id, e.email FROM employee_daily_metrics m JOIN employees e ON e.employee_id = m.employee_id " +
                "WHERE m.metrics_date = ? AND m.status = 'INCOMPLETE' LIMIT 500", today);
        int sent = 0;
        for (Map<String, Object> row : rows) {
            String employeeId = String.valueOf(row.get("employee_id"));
            String email = row.get("email") == null ? null : String.valueOf(row.get("email"));
            notificationService.notifyWithRecipient(employeeId, "MISSING_CHECKOUT_DIGEST",
                    Map.of("date", today.toString()), "/attendance", "NOCHECKOUT-" + employeeId + "-" + today, email);
            sent++;
        }
        logger.info("MissingCheckoutDigestJob: {} digest notification(s) for {}", sent, today);
    }
}
