package com.mentis.hrms.service;

import com.mentis.hrms.model.OnboardingDocumentMeta;
import com.mentis.hrms.repository.OnboardingDocumentMetaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REQ-8-02 — harden document verification against the EXISTING onboarding_documents table
 * (columns verified on Drive: status PENDING|SUBMITTED|VERIFIED|REJECTED, verificationNotes,
 *  verifiedBy, verifiedDate, mandatory). Re-upload = NEW row via the legacy flow; reject keeps history row.
 * Expiring docs: side table onboarding_document_meta (expires_on, remind_sent) — legacy schema unchanged.
 */
@Service
public class DocumentVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentVerificationService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OnboardingDocumentMetaRepository metaRepository;

    @Autowired(required = false)
    private HrmsNotificationService notificationService;

    @Value("${hrms.document-expiry.remind-days:14}")
    private int remindDays;

    @Transactional
    public void verify(Long documentId, String verifier) {
        requireVerifier(verifier);
        int rows = jdbcTemplate.update(
                "UPDATE onboarding_documents SET status = 'VERIFIED', verified_by = ?, verified_date = ? WHERE id = ?",
                verifier, LocalDateTime.now(), documentId);
        if (rows == 0) throw new IllegalArgumentException("Document not found: " + documentId);
    }

    /** Reject with reason (mandatory). Employee is notified and must re-upload (new row). */
    @Transactional
    public void reject(Long documentId, String reason, String verifier) {
        requireVerifier(verifier);
        if (reason == null || reason.trim().length() < 5) {
            throw new IllegalArgumentException("Rejection reason (min 5 chars) is required");
        }
        Long employeePk = jdbcTemplate.query(
                "SELECT employee_id FROM onboarding_documents WHERE id = ?",
                rs -> rs.next() ? rs.getLong(1) : null, documentId);
        int rows = jdbcTemplate.update(
                "UPDATE onboarding_documents SET status = 'REJECTED', verification_notes = ?, verified_by = ?, verified_date = ? WHERE id = ?",
                reason.trim(), verifier, LocalDateTime.now(), documentId);
        if (rows == 0) throw new IllegalArgumentException("Document not found: " + documentId);

        if (notificationService != null && employeePk != null) {
            String employeeId = jdbcTemplate.query(
                    "SELECT employee_id FROM employees WHERE id = ?",
                    rs -> rs.next() ? rs.getString(1) : null, employeePk);
            if (employeeId != null) {
                notificationService.notify(employeeId, "GENERIC",
                        Map.of("title", "Document rejected — re-upload required",
                               "body", "Your document #" + documentId + " was rejected: " + reason.trim()),
                        "/onboarding/documents", "DOCREJECT-" + documentId);
            }
        }
    }

    @Transactional
    public OnboardingDocumentMeta setExpiry(Long documentId, LocalDate expiresOn) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM onboarding_documents WHERE id = ?", Integer.class, documentId);
        if (exists == null || exists == 0) throw new IllegalArgumentException("Document not found: " + documentId);
        OnboardingDocumentMeta meta = metaRepository.findById(documentId).orElseGet(() -> {
            OnboardingDocumentMeta m = new OnboardingDocumentMeta();
            m.setDocumentId(documentId);
            return m;
        });
        meta.setExpiresOn(expiresOn);
        meta.setRemindSent(null); // reset so a fresh reminder is sent
        return metaRepository.save(meta);
    }

    /** Daily: docs expiring within remindDays, not yet reminded → notify owner (+HR), stamp remind_sent. */
    @Scheduled(cron = "${hrms.document-expiry.cron:0 20 7 * * *}")
    @Transactional
    public void remindExpiring() {
        LocalDate today = LocalDate.now();
        List<OnboardingDocumentMeta> due = metaRepository.findByExpiresOnBetweenAndRemindSentIsNull(
                today, today.plusDays(remindDays));
        int sent = 0;
        for (OnboardingDocumentMeta meta : due) {
            try {
                Long employeePk = jdbcTemplate.query(
                        "SELECT employee_id FROM onboarding_documents WHERE id = ?",
                        rs -> rs.next() ? rs.getLong(1) : null, meta.getDocumentId());
                if (employeePk != null) {
                    String employeeId = jdbcTemplate.query(
                            "SELECT employee_id FROM employees WHERE id = ?",
                            rs -> rs.next() ? rs.getString(1) : null, employeePk);
                    if (notificationService != null && employeeId != null) {
                        notificationService.notify(employeeId, "GENERIC",
                                Map.of("title", "Document expiring soon",
                                       "body", "Document #" + meta.getDocumentId() + " expires on " + meta.getExpiresOn() + ". Please renew and re-upload."),
                                "/onboarding/documents", "DOCEXP-" + meta.getDocumentId() + "-" + meta.getExpiresOn());
                    }
                    meta.setRemindSent(LocalDateTime.now());
                    metaRepository.save(meta);
                    sent++;
                }
            } catch (Exception e) {
                logger.error("Expiry reminder failed for document {}: {}", meta.getDocumentId(), e.getMessage());
            }
        }
        if (sent > 0) logger.info("DocumentVerificationService: {} expiry reminder(s) sent", sent);
    }

    /** Count mandatory DOCUMENTS not yet VERIFIED for an employee (used by onboarding completion rule). */
    public long mandatoryDocsNotVerified(String employeeId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM onboarding_documents od JOIN employees e ON e.id = od.employee_id " +
                "WHERE e.employee_id = ? AND od.mandatory = TRUE AND od.status <> 'VERIFIED'",
                Long.class, employeeId);
        return count == null ? 0 : count;
    }

    public long mandatoryDocsTotal(String employeeId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM onboarding_documents od JOIN employees e ON e.id = od.employee_id " +
                "WHERE e.employee_id = ? AND od.mandatory = TRUE",
                Long.class, employeeId);
        return count == null ? 0 : count;
    }

    private static void requireVerifier(String verifier) {
        if (verifier == null || verifier.isBlank()) {
            throw new IllegalArgumentException("verifier (user id) is required");
        }
    }
}
