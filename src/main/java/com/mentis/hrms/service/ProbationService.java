package com.mentis.hrms.service;

import com.mentis.hrms.model.ProbationReview;
import com.mentis.hrms.repository.ProbationReviewRepository;
import com.mentis.hrms.util.LifecycleRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * REQ-8-06 — probation reviews: auto-create 30 days before probation_end_date,
 * 30/14/7-day reminders, overdue escalation to HR, manager recommendation + HR decision.
 * Confirmation posts the confirmation-coupled leave grants seeded in leave_policy_confirmation_grant.
 * Everything writes via native SQL for V3 columns (Employee entity does not map them).
 */
@Service
public class ProbationService {

    private static final Logger logger = LoggerFactory.getLogger(ProbationService.class);

    @Autowired private ProbationReviewRepository reviewRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LifecycleSupport support;
    @Autowired private AuditWriter auditWriter;

    @Autowired(required = false)
    @Lazy
    private HrmsNotificationService notificationService;

    @Value("${hrms.probation.auto-create:true}")
    private boolean autoCreate;

    /** Daily 06:30 — create reviews for PROBATION employees whose end date is within 30 days. */
    @Scheduled(cron = "${hrms.probation.autocreate.cron:0 30 6 * * *}")
    @Transactional
    public int autoCreateReviews() {
        if (!autoCreate) return 0;
        List<Map<String, Object>> due = jdbcTemplate.queryForList(
                "SELECT id, employee_id, probation_end_date FROM employees " +
                "WHERE employment_status = 'PROBATION' AND probation_end_date IS NOT NULL " +
                "AND probation_end_date BETWEEN CURRENT_DATE AND DATE_ADD(CURRENT_DATE, INTERVAL 30 DAY)");
        int created = 0;
        for (Map<String, Object> row : due) {
            String employeeId = (String) row.get("employee_id");
            LocalDate end = toLocalDate(row.get("probation_end_date"));
            if (employeeId == null || end == null) continue;
            if (reviewRepository.existsByEmployeeIdAndStatus(employeeId, "PENDING")) continue; // idempotent

            ProbationReview review = new ProbationReview();
            review.setEmployeeId(employeeId);
            review.setEmployeePk(toLong(row.get("id")));
            review.setDueDate(end);           // review due ON probation end date
            review.setPeriodEnd(end);
            reviewRepository.save(review);
            notify(employeeId, "PROBATION_REVIEW_DUE",
                    Map.of("employee", employeeId, "date", end.toString()),
                    "/probation/reviews", "PROBREV-" + employeeId + "-" + end);
            created++;
        }
        if (created > 0) logger.info("ProbationService: auto-created {} probation review(s)", created);
        return created;
    }

    /** Daily 08:00 — 30/14/7-day reminders + overdue escalation to HR. */
    @Scheduled(cron = "${hrms.probation.reminders.cron:0 0 8 * * *}")
    @Transactional
    public void sendReminders() {
        LocalDate today = LocalDate.now();
        for (ProbationReview r : reviewRepository.findByStatusOrderByDueDateAsc("PENDING")) {
            long days = ChronoUnit.DAYS.between(today, r.getDueDate());
            boolean dirty = false;
            if (days == 30 && !r.isReminder30Sent()) { remindManager(r, 30); r.setReminder30Sent(true); dirty = true; }
            else if (days == 14 && !r.isReminder14Sent()) { remindManager(r, 14); r.setReminder14Sent(true); dirty = true; }
            else if (days == 7 && !r.isReminder7Sent()) { remindManager(r, 7); r.setReminder7Sent(true); dirty = true; }
            else if (days < 0 && !r.isEscalationSent()) { escalateToHr(r, days); r.setEscalationSent(true); dirty = true; }
            if (dirty) reviewRepository.save(r);
        }
    }

    /** Manager submits the review recommendation. HR makes the final call via hrDecision(). */
    @Transactional
    public ProbationReview submitManagerReview(Long reviewId, int managerRating, String competencyRatingsJson,
                                               String recommendation, Integer extensionDays,
                                               String comments, String managerUserId) {
        if (managerRating < 1 || managerRating > 5) {
            throw new IllegalArgumentException("managerRating must be between 1 and 5");
        }
        if (!LifecycleRules.RECOMMENDATIONS.contains(recommendation)) {
            throw new IllegalArgumentException("recommendation must be one of " + LifecycleRules.RECOMMENDATIONS);
        }
        if ("EXTEND".equals(recommendation) && (extensionDays == null || extensionDays < 1 || extensionDays > 180)) {
            throw new IllegalArgumentException("EXTEND requires extensionDays between 1 and 180");
        }
        List<String> problems = LifecycleRules.validateManagerDecision(recommendation, managerRating, extensionDays, comments);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", problems));
        }
        ProbationReview review = mustGet(reviewId);
        if ("DECIDED".equals(review.getStatus())) {
            throw new IllegalStateException("Review already decided (id=" + reviewId + ")");
        }
        review.setManagerRating(managerRating);
        review.setCompetencyRatings(competencyRatingsJson);
        review.setRecommendation(recommendation);
        review.setExtensionDays("EXTEND".equals(recommendation) ? extensionDays : null);
        review.setComments(comments);
        review = reviewRepository.save(review);
        auditWriter.write("PROBATION_MANAGER_REVIEW", "probation_review", String.valueOf(reviewId),
                AuditWriter.json("recommendation", recommendation, "rating", String.valueOf(managerRating)),
                managerUserId);
        return review;
    }

    /**
     * HR gate. APPROVED applies the manager recommendation; REJECTED kicks the review back to the manager
     * (status stays PENDING so the manager can resubmit).
     */
    @Transactional
    public ProbationReview hrDecision(Long reviewId, String hrDecision, String hrUserId, String note) {
        if (!"APPROVED".equals(hrDecision) && !"REJECTED".equals(hrDecision)) {
            throw new IllegalArgumentException("hrDecision must be APPROVED or REJECTED");
        }
        ProbationReview review = mustGet(reviewId);
        if ("DECIDED".equals(review.getStatus())) {
            throw new IllegalStateException("Review already decided (id=" + reviewId + ")");
        }
        if (review.getRecommendation() == null) {
            throw new IllegalStateException("Manager has not submitted a recommendation yet");
        }
        review.setHrDecision(hrDecision);
        review.setHrDecider(hrUserId);
        review.setHrDecidedAt(LocalDateTime.now());

        if ("REJECTED".equals(hrDecision)) {
            review = reviewRepository.save(review); // stays PENDING for re-review
            auditWriter.write("PROBATION_HR_REJECTED", "probation_review", String.valueOf(reviewId),
                    AuditWriter.json("note", note == null ? "" : note), hrUserId);
            return review;
        }

        String rec = review.getRecommendation();
        Long employeePk = review.getEmployeePk() != null ? review.getEmployeePk() : support.employeePk(review.getEmployeeId());
        LocalDate today = LocalDate.now();
        switch (rec) {
            case "CONFIRM" -> confirm(review, employeePk, today, hrUserId);
            case "EXTEND"  -> extend(review, employeePk, hrUserId);
            case "TERMINATE" -> terminate(review, employeePk, today, hrUserId);
            default -> throw new IllegalStateException("Unknown recommendation: " + rec);
        }
        review.setStatus("DECIDED");
        review = reviewRepository.save(review);
        auditWriter.write("PROBATION_HR_APPROVED_" + rec, "probation_review", String.valueOf(reviewId),
                AuditWriter.json("employee", review.getEmployeeId()), hrUserId);
        return review;
    }

    private void confirm(ProbationReview review, Long employeePk, LocalDate today, String hrUserId) {
        support.setConfirmed(employeePk, today);
        support.jobHistory(employeePk, "PROBATION_CONFIRMED", today, hrUserId);
        // Confirmation-coupled leave grants — HR seeds leave_policy_confirmation_grant; nothing is invented here.
        List<Map<String, Object>> grants = jdbcTemplate.queryForList(
                "SELECT g.leave_policy_id, g.grant_days, p.leave_type_id " +
                "FROM leave_policy_confirmation_grant g JOIN leave_policy p ON p.id = g.leave_policy_id");
        for (Map<String, Object> grant : grants) {
            Number leaveTypeId = (Number) grant.get("leave_type_id");
            Number grantDays = (Number) grant.get("grant_days"); // DECIMAL — BigDecimal implements Number
            if (leaveTypeId == null || grantDays == null) continue;
            // Idempotency: ledger uq (reference_type, reference_id, entry_type, leave_type_id, employee_id)
            jdbcTemplate.update(
                    "INSERT IGNORE INTO leave_ledger (employee_id, leave_type_id, entry_date, entry_type, amount, " +
                    "reference_type, reference_id, remarks, created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    review.getEmployeeId(), leaveTypeId.longValue(), today, "CREDIT_MANUAL",
                    grantDays, "confirmation", String.valueOf(review.getId()),
                    "Confirmation-coupled grant (policy " + grant.get("leave_policy_id") + ")", hrUserId);
        }
        notify(review.getEmployeeId(), "GENERIC",
                Map.of("title", "Congratulations — you are confirmed!",
                       "body", "Your probation is complete. Confirmation date: " + today),
                "/profile", "CONFIRM-" + review.getId());
    }

    private void extend(ProbationReview review, Long employeePk, String hrUserId) {
        int days = review.getExtensionDays() == null ? 30 : review.getExtensionDays();
        LocalDate currentEnd = null;
        Map<String, Object> emp = support.employeeByEmployeeId(review.getEmployeeId());
        if (emp != null) currentEnd = toLocalDate(emp.get("probation_end_date"));
        if (currentEnd == null) currentEnd = review.getDueDate();
        LocalDate newEnd = currentEnd.plusDays(days);
        support.setProbationEndDate(employeePk, newEnd);
        support.jobHistory(employeePk, "PROBATION_EXTENDED_" + days + "D", LocalDate.now(), hrUserId);
        // Re-review: create the follow-up review now so reminders/escalation run on the new end date.
        ProbationReview followUp = new ProbationReview();
        followUp.setEmployeeId(review.getEmployeeId());
        followUp.setEmployeePk(employeePk);
        followUp.setDueDate(newEnd);
        followUp.setPeriodEnd(newEnd);
        reviewRepository.save(followUp);
        notify(review.getEmployeeId(), "GENERIC",
                Map.of("title", "Probation extended",
                       "body", "Your probation is extended by " + days + " days (new end date: " + newEnd + ")."),
                "/profile", "PROBEXT-" + review.getId());
    }

    private void terminate(ProbationReview review, Long employeePk, LocalDate today, String hrUserId) {
        support.setInactive(employeePk, today, "PROBATION_TERMINATED");
        support.jobHistory(employeePk, "PROBATION_TERMINATED", today, hrUserId);
        notify(review.getEmployeeId(), "GENERIC",
                Map.of("title", "Probation outcome",
                       "body", "Your employment is ending following the probation review. Please contact HR."),
                "/profile", "PROBTERM-" + review.getId());
    }

    public List<ProbationReview> pendingReviews() {
        return reviewRepository.findByStatusOrderByDueDateAsc("PENDING");
    }

    private ProbationReview mustGet(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Probation review not found: " + reviewId));
    }

    private void remindManager(ProbationReview r, int days) {
        String manager = support.managerEmployeeIdOf(r.getEmployeeId());
        String target = manager != null ? manager : "HR";
        notify(target, "PROBATION_REVIEW_DUE",
                Map.of("employee", r.getEmployeeId(), "date", r.getDueDate().toString()),
                "/probation/reviews", "PROBREM-" + r.getId() + "-" + days);
        logger.info("Probation reminder ({}d) sent for review {} -> {}", days, r.getId(), target);
    }

    private void escalateToHr(ProbationReview r, long overdueDays) {
        notify("HR", "PROBATION_REVIEW_DUE",
                Map.of("employee", r.getEmployeeId(), "date", r.getDueDate().toString()),
                "/probation/reviews", "PROBESC-" + r.getId());
        logger.warn("Probation review {} overdue by {} day(s) — escalated to HR", r.getId(), -overdueDays);
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

    private static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.time.LocalDate d) return d;
        return LocalDate.parse(value.toString());
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        return value == null ? null : Long.parseLong(value.toString());
    }
}
