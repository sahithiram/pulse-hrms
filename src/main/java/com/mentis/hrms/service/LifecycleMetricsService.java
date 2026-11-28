package com.mentis.hrms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** Lifecycle metrics for dashboards (Sprint 8). Read-only; every query is native SQL. */
@Service
public class LifecycleMetricsService {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ExitService exitService;

    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("onboardingInFlight", count(
                "SELECT COUNT(*) FROM employees WHERE onboarding_status IS NOT NULL " +
                "AND onboarding_status NOT IN ('COMPLETED','CANCELLED') AND active = 1"));
        m.put("onboardingCompletedThisMonth", count(
                "SELECT COUNT(*) FROM employees WHERE onboarding_completed_date >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')"));
        m.put("avgOnboardingDays", decimal(
                "SELECT AVG(DATEDIFF(onboarding_completed_date, onboarding_start_date)) " +
                "FROM employees WHERE onboarding_completed_date IS NOT NULL AND onboarding_start_date IS NOT NULL"));
        m.put("mandatoryTasksOpen", count(
                "SELECT COUNT(*) FROM onboarding_task WHERE mandatory = 1 AND status NOT IN ('DONE','SKIPPED')"));
        m.put("probationReviewsPending", count(
                "SELECT COUNT(*) FROM probation_review WHERE status = 'PENDING'"));
        m.put("probationReviewsOverdue", count(
                "SELECT COUNT(*) FROM probation_review WHERE status = 'PENDING' AND due_date < CURRENT_DATE"));
        m.put("resignationsSubmitted", count(
                "SELECT COUNT(*) FROM resignation WHERE status = 'SUBMITTED'"));
        m.put("resignationsInNotice", count(
                "SELECT COUNT(*) FROM resignation WHERE status = 'ACCEPTED'"));
        m.put("exitsThisMonth", count(
                "SELECT COUNT(*) FROM employees WHERE exit_date >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01') " +
                "AND employment_status = 'EXITED'"));
        m.put("exitChecklistItemsPending", count(
                "SELECT COUNT(*) FROM exit_checklist WHERE status = 'PENDING'"));
        m.put("feedbackDay7Submitted", count(
                "SELECT COUNT(*) FROM onboarding_feedback WHERE day = 7"));
        m.put("feedbackDay30Submitted", count(
                "SELECT COUNT(*) FROM onboarding_feedback WHERE day = 30"));
        return m;
    }

    public Map<String, Object> employeeLifecycleSummary(String employeeId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("onboardingTasks", jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS n FROM onboarding_task WHERE employee_id = ? GROUP BY status", employeeId));
        summary.put("exitChecklistPending", exitService.checklistPending(employeeId));
        summary.put("feedback", jdbcTemplate.queryForList(
                "SELECT day, submitted_at FROM onboarding_feedback WHERE employee_id = ? ORDER BY day", employeeId));
        return summary;
    }

    private long count(String sql, Object... args) {
        Long n = jdbcTemplate.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    private Double decimal(String sql, Object... args) {
        Double n = jdbcTemplate.queryForObject(sql, Double.class, args);
        return n;
    }
}
