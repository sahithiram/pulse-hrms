package com.mentis.hrms.service;

import com.mentis.hrms.model.OnboardingFeedback;
import com.mentis.hrms.repository.OnboardingFeedbackRepository;
import com.mentis.hrms.repository.OnboardingTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REQ-8-03 — onboarding completion rule: all mandatory tasks DONE/SKIPPED + all mandatory documents VERIFIED
 *              ⇒ employment_status=PROBATION + onboarding_status=COMPLETED + notify manager/HR.
 *              Manual override always requires an explicit reason (audited).
 * REQ-8-04 — day-7 / day-30 feedback survey; results visible to HR.
 */
@Service
public class OnboardingLifecycleService {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingLifecycleService.class);

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LifecycleSupport support;
    @Autowired private AuditWriter auditWriter;
    @Autowired private OnboardingTaskRepository taskRepository;
    @Autowired private DocumentVerificationService documentVerificationService;
    @Autowired private OnboardingFeedbackRepository feedbackRepository;

    @Autowired(required = false)
    private HrmsNotificationService notificationService;

    public Map<String, Object> completionReport(String employeeId) {
        Map<String, Object> report = new LinkedHashMap<>();
        long mandatoryTasksPending =
                taskRepository.countByEmployeeIdAndMandatoryTrueAndStatusNotIn(employeeId, List.of("DONE", "SKIPPED"));
        Map<String, Object> taskStats = taskStats(employeeId);
        long mandatoryDocsPending = documentVerificationService.mandatoryDocsNotVerified(employeeId);
        long mandatoryDocsTotal = documentVerificationService.mandatoryDocsTotal(employeeId);
        Map<String, Object> emp = support.employeeByEmployeeId(employeeId);

        report.put("employeeId", employeeId);
        report.put("taskStats", taskStats);
        report.put("mandatoryTasksPending", mandatoryTasksPending);
        report.put("mandatoryDocsPending", mandatoryDocsPending);
        report.put("mandatoryDocsTotal", mandatoryDocsTotal);
        report.put("employmentStatus", emp != null ? emp.get("employment_status") : null);
        report.put("onboardingStatus", emp != null ? emp.get("onboarding_status") : null);
        report.put("completable", mandatoryTasksPending == 0 && mandatoryDocsPending == 0);
        return report;
    }

    /** Promote ONBOARDING → PROBATION when (and only when) the completion rule holds. */
    @Transactional
    public Map<String, Object> maybeComplete(String employeeId, String actorUserId) {
        Map<String, Object> report = completionReport(employeeId);
        if (!Boolean.TRUE.equals(report.get("completable"))) {
            report.put("promoted", false);
            report.put("reason", "mandatory tasks/documents outstanding");
            return report;
        }
        Map<String, Object> emp = support.employeeByEmployeeId(employeeId);
        if (emp == null) throw new IllegalArgumentException("Employee not found: " + employeeId);
        String status = String.valueOf(emp.get("employment_status"));
        if (!"ONBOARDING".equals(status) && !"PROBATION".equals(status)) {
            report.put("promoted", false);
            report.put("reason", "unexpected lifecycle status " + status);
            return report;
        }
        Long pk = ((Number) emp.get("id")).longValue();
        if ("ONBOARDING".equals(status)) {
            jdbcTemplate.update(
                    "UPDATE employees SET employment_status = 'PROBATION', onboarding_status = 'COMPLETED', " +
                    "onboarding_completed_date = ? WHERE id = ?", LocalDateTime.now(), pk);
            support.jobHistory(pk, "ONBOARDING_COMPLETED", LocalDate.now(), actorUserId);
            notify(employeeId, "GENERIC",
                    Map.of("title", "Onboarding complete",
                           "body", "Your onboarding is complete. You are now on probation."),
                    "/onboarding/employee/" + employeeId, "ONBCOMP-" + employeeId);
            String manager = support.managerEmployeeIdOf(employeeId);
            if (manager != null) {
                notify(manager, "GENERIC",
                        Map.of("title", "Onboarding complete",
                               "body", employeeId + " finished onboarding and is now on probation."),
                        "/onboarding/employee/" + employeeId, "ONBCOMPMGR-" + employeeId);
            }
            notify("HR", "GENERIC",
                    Map.of("title", "Onboarding complete",
                           "body", employeeId + " finished onboarding and is now on probation."),
                    "/onboarding/employee/" + employeeId, "ONBCOMPHR-" + employeeId);
            auditWriter.write("ONBOARDING_COMPLETED", "employee", employeeId,
                    AuditWriter.json("from", "ONBOARDING", "to", "PROBATION"), actorUserId);
            report.put("promoted", true);
        } else {
            report.put("promoted", false);
            report.put("reason", "already in PROBATION");
        }
        return report;
    }

    /** Manual override — reason is mandatory and audited. */
    @Transactional
    public Map<String, Object> overrideComplete(String employeeId, String reason, String actorUserId) {
        if (reason == null || reason.trim().length() < 10) {
            throw new IllegalArgumentException("Override reason (min 10 chars) is required");
        }
        Map<String, Object> emp = support.employeeByEmployeeId(employeeId);
        if (emp == null) throw new IllegalArgumentException("Employee not found: " + employeeId);
        String status = String.valueOf(emp.get("employment_status"));
        if ("ONBOARDING".equals(status)) {
            Long pk = ((Number) emp.get("id")).longValue();
            jdbcTemplate.update(
                    "UPDATE employees SET employment_status = 'PROBATION', onboarding_status = 'COMPLETED', " +
                    "onboarding_completed_date = ? WHERE id = ?", LocalDateTime.now(), pk);
            support.jobHistory(pk, "ONBOARDING_OVERRIDE", LocalDate.now(), actorUserId);
        }
        auditWriter.write("ONBOARDING_OVERRIDE", "employee", employeeId,
                AuditWriter.json("reason", reason.trim()), actorUserId);
        Map<String, Object> report = completionReport(employeeId);
        report.put("override", true);
        report.put("promoted", true);
        return report;
    }

    /* ---------- REQ-8-04 feedback ---------- */

    /** Daily 09:00 — ping employees whose onboarding started exactly 6/29 days ago that feedback is due tomorrow. */
    @Scheduled(cron = "${hrms.onboarding.feedback.cron:0 0 9 * * *}")
    @Transactional
    public void remindFeedbackDue() {
        for (int day : List.of(7, 30)) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT employee_id FROM employees " +
                    "WHERE onboarding_start_date IS NOT NULL " +
                    "AND DATE(onboarding_start_date) = DATE_SUB(CURRENT_DATE, INTERVAL ? DAY) " +
                    "AND active = 1", day);
            for (Map<String, Object> row : rows) {
                String employeeId = (String) row.get("employee_id");
                if (feedbackRepository.findByEmployeeIdAndDay(employeeId, day).isPresent()) continue; // already done
                notify(employeeId, "GENERIC",
                        Map.of("title", "Onboarding feedback — day " + day,
                               "body", "Please take 2 minutes to share feedback on your onboarding (day " + day + ")."),
                        "/onboarding/feedback?day=" + day, "ONBFB-" + employeeId + "-" + day);
            }
        }
    }

    @Transactional
    public OnboardingFeedback submitFeedback(String employeeId, int day, String ratingsJson,
                                             String comments, String actorUserId) {
        if (day != 7 && day != 30) {
            throw new IllegalArgumentException("day must be 7 or 30");
        }
        if (feedbackRepository.findByEmployeeIdAndDay(employeeId, day).isPresent()) {
            throw new IllegalStateException("Day-" + day + " feedback already submitted for " + employeeId);
        }
        OnboardingFeedback feedback = new OnboardingFeedback();
        feedback.setEmployeeId(employeeId);
        feedback.setDay(day);
        feedback.setRatingsJson(ratingsJson);
        feedback.setComments(comments);
        feedback = feedbackRepository.save(feedback);
        auditWriter.write("ONBOARDING_FEEDBACK", "onboarding_feedback", String.valueOf(feedback.getId()),
                AuditWriter.json("employee", employeeId, "day", String.valueOf(day)), actorUserId);
        return feedback;
    }

    /** HR view — all feedback rows (visible to HR only at the controller layer). */
    public List<OnboardingFeedback> feedbackFor(String employeeId) {
        return feedbackRepository.findByEmployeeId(employeeId);
    }

    private Map<String, Object> taskStats(String employeeId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS n FROM onboarding_task WHERE employee_id = ? GROUP BY status", employeeId);
        long total = 0, doneOrSkipped = 0;
        for (Map<String, Object> row : rows) {
            String status = String.valueOf(row.get("status"));
            long n = ((Number) row.get("n")).longValue();
            stats.put(status, n);
            total += n;
            if ("DONE".equals(status) || "SKIPPED".equals(status)) doneOrSkipped += n;
        }
        stats.put("total", total);
        stats.put("completionPct", total == 0 ? 0 : (doneOrSkipped * 100) / total);
        return stats;
    }

    private void notify(String userId, String eventType, Map<String, String> placeholders,
                        String deepLink, String eventId) {
        if (notificationService == null) {
            logger.debug("Notification service not wired; skipped {} to {}", eventType, userId);
            return;
        }
        try {
            notificationService.notify(userId, eventType, placeholders, deepLink, eventId);
        } catch (Exception e) {
            logger.error("Failed to send {} to {}: {}", eventType, userId, e.getMessage());
        }
    }
}
