package com.mentis.hrms.service;

import com.mentis.hrms.model.Resignation;
import com.mentis.hrms.repository.ExitChecklistRepository;
import com.mentis.hrms.repository.ResignationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * REQ-8-07 — resignation lifecycle: SUBMITTED → ACCEPTED → COMPLETED (or REVOKED).
 * Notice period comes from notice_rule (seeded FULL_TIME 30 / PART_TIME 15 / CONTRACT 30 / INTERN 7,
 * grade-specific rows supported via UNIQUE(employment_type, grade), '__ANY__' = default). Values are HR-seeded
 * data, change rows not code. Shortfall may be waived via notice buyout.
 */
@Service
public class ResignationService {

    private static final Logger logger = LoggerFactory.getLogger(ResignationService.class);

    @Autowired private ResignationRepository resignationRepository;
    @Autowired private ExitChecklistRepository exitChecklistRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LifecycleSupport support;
    @Autowired private AuditWriter auditWriter;
    @Autowired private ExitService exitService;

    @Autowired(required = false)
    private HrmsNotificationService notificationService;

    @Transactional
    public Resignation submit(String employeeId, LocalDate requestedLastDay, String reason, String submittedBy) {
        Map<String, Object> emp = support.employeeByEmployeeId(employeeId);
        if (emp == null) throw new IllegalArgumentException("Employee not found: " + employeeId);

        String status = String.valueOf(emp.get("employment_status"));
        if ("EXITED".equals(status) || "INACTIVE".equals(status)) {
            throw new IllegalStateException("Employee is " + status + " — cannot resign");
        }
        if ("NOTICE".equals(status)) {
            throw new IllegalStateException("Employee is already in notice period");
        }
        resignationRepository.findByEmployeeIdAndStatus(employeeId, "SUBMITTED").ifPresent(r -> {
            throw new IllegalStateException("A resignation is already pending (id=" + r.getId() + ")");
        });
        resignationRepository.findByEmployeeIdAndStatus(employeeId, "ACCEPTED").ifPresent(r -> {
            throw new IllegalStateException("A resignation was already accepted (id=" + r.getId() + ")");
        });

        LocalDate today = LocalDate.now();
        if (requestedLastDay == null || requestedLastDay.isBefore(today)) {
            throw new IllegalArgumentException("requestedLastDay must be today or later");
        }

        String employmentType = coalesce(
                emp.get("employment_type") == null ? null : emp.get("employment_type").toString(),
                emp.get("employment_mode") == null ? null : emp.get("employment_mode").toString(),
                "FULL_TIME").toUpperCase().trim();
        int required = noticeDaysFor(employmentType);

        Resignation r = new Resignation();
        r.setEmployeeId(employeeId);
        r.setSubmittedOn(today);
        r.setRequestedLastDay(requestedLastDay);
        r.setReason(reason);
        r.setStatus("SUBMITTED");
        r.setNoticeDaysRequired(required);
        r.setNoticeShortfall((int) Math.max(0, required - ChronoUnit.DAYS.between(today, requestedLastDay)));
        r.setSubmittedBy(submittedBy);
        r = resignationRepository.save(r);

        auditWriter.write("RESIGNATION_SUBMITTED", "resignation", String.valueOf(r.getId()),
                AuditWriter.json("employee", employeeId, "requestedLastDay", String.valueOf(requestedLastDay),
                        "noticeDaysRequired", String.valueOf(required)), submittedBy);
        notify("HR", "GENERIC",
                Map.of("title", "Resignation submitted",
                       "body", employeeId + " submitted resignation; requested last day " + requestedLastDay + "."),
                "/resignations", "RESIG-" + r.getId());
        return r;
    }

    /**
     * Accept. If acceptedLastDay is null the default is max(requestedLastDay, submittedOn + requiredNotice);
     * when noticeBuyout is requested the requested last day stands and the shortfall is waived.
     */
    @Transactional
    public Resignation accept(Long resignationId, LocalDate acceptedLastDay, boolean noticeBuyout, String decider) {
        Resignation r = mustGet(resignationId);
        if (!"SUBMITTED".equals(r.getStatus())) {
            throw new IllegalStateException("Resignation is " + r.getStatus() + " — accept only from SUBMITTED");
        }
        LocalDate defaultDay = r.getSubmittedOn().plusDays(r.getNoticeDaysRequired());
        LocalDate accepted = acceptedLastDay != null ? acceptedLastDay
                : (noticeBuyout ? r.getRequestedLastDay() : max(r.getRequestedLastDay(), defaultDay));

        r.setStatus("ACCEPTED");
        r.setAcceptedLastDay(accepted);
        r.setNoticeBuyout(noticeBuyout);
        r.setNoticeShortfall((int) Math.max(0, r.getNoticeDaysRequired() - ChronoUnit.DAYS.between(r.getSubmittedOn(), accepted)));

        Map<String, Object> emp = support.employeeByEmployeeId(r.getEmployeeId());
        if (emp != null) {
            r.setPrevEmploymentStatus(String.valueOf(emp.get("employment_status")));
            Long pk = ((Number) emp.get("id")).longValue();
            support.setNoticeStarted(pk, LocalDate.now());
            support.jobHistory(pk, "RESIGNATION_ACCEPTED", LocalDate.now(), decider);
        }
        r = resignationRepository.save(r);

        exitService.instantiateChecklist(r.getEmployeeId(), accepted, decider);

        String manager = support.managerEmployeeIdOf(r.getEmployeeId());
        String deepLink = "/resignations";
        if (manager != null) {
            notify(manager, "GENERIC",
                    Map.of("title", "Resignation accepted",
                           "body", r.getEmployeeId() + " is serving notice until " + accepted + "."),
                    deepLink, "RESIGACC-MGR-" + r.getId());
        }
        notify(r.getEmployeeId(), "GENERIC",
                Map.of("title", "Resignation accepted",
                       "body", "Your last working day is " + accepted + (noticeBuyout ? " (notice buyout applied)." : ".")),
                deepLink, "RESIGACC-" + r.getId());
        auditWriter.write("RESIGNATION_ACCEPTED", "resignation", String.valueOf(r.getId()),
                AuditWriter.json("employee", r.getEmployeeId(), "acceptedLastDay", String.valueOf(accepted),
                        "noticeBuyout", String.valueOf(noticeBuyout)), decider);
        return r;
    }

    /** Revoke while the last working day has not passed and the exit has not been finalized. */
    @Transactional
    public Resignation revoke(Long resignationId, String reason, String actor) {
        Resignation r = mustGet(resignationId);
        if (!("SUBMITTED".equals(r.getStatus()) || "ACCEPTED".equals(r.getStatus()))) {
            throw new IllegalStateException("Cannot revoke a " + r.getStatus() + " resignation");
        }
        if (r.getAcceptedLastDay() != null && r.getAcceptedLastDay().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Last working day has passed — exit cannot be revoked by this flow");
        }
        String prev = r.getPrevEmploymentStatus();
        r.setStatus("REVOKED");
        r.setRevokedAt(LocalDateTime.now());
        r.setRevokeReason(reason);

        Map<String, Object> emp = support.employeeByEmployeeId(r.getEmployeeId());
        if (emp != null && prev != null) {
            Long pk = ((Number) emp.get("id")).longValue();
            jdbcTemplate.update("UPDATE employees SET employment_status = ?, notice_start_date = NULL WHERE id = ?",
                    prev, pk);
            support.jobHistory(pk, "RESIGNATION_REVOKED", LocalDate.now(), actor);
        }
        // Outstanding exit checklist items become NA
        exitChecklistRepository.findByEmployeeIdOrderByTaskAsc(r.getEmployeeId()).forEach(task -> {
            if (!"DONE".equals(task.getStatus())) {
                task.setStatus("NA");
                task.setNotes("Revoked: " + (reason == null ? "resignation withdrawn" : reason));
                exitChecklistRepository.save(task);
            }
        });
        r = resignationRepository.save(r);
        auditWriter.write("RESIGNATION_REVOKED", "resignation", String.valueOf(r.getId()),
                AuditWriter.json("employee", r.getEmployeeId(), "reason", reason == null ? "" : reason), actor);
        notify("HR", "GENERIC",
                Map.of("title", "Resignation revoked",
                       "body", r.getEmployeeId() + " withdrew their resignation."),
                "/resignations", "RESIGREV-" + r.getId());
        return r;
    }

    /** Daily 07:00 — finalize employees whose accepted last working day has arrived. */
    @Scheduled(cron = "${hrms.resignation.complete.cron:0 0 7 * * *}")
    @Transactional
    public int completeOverdueExits() {
        int finalized = 0;
        for (Resignation r : resignationRepository.findByStatusAndAcceptedLastDayLessThanEqual("ACCEPTED", LocalDate.now())) {
            exitService.finalizeExit(r.getEmployeeId(), "RESIGNATION", "SYSTEM");
            finalized++;
        }
        if (finalized > 0) logger.info("ResignationService: finalized {} exit(s)", finalized);
        return finalized;
    }

    public List<Resignation> recent() {
        return resignationRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public int noticeDaysFor(String employmentType) {
        List<Integer> rows = jdbcTemplate.query(
                "SELECT notice_days FROM notice_rule WHERE employment_type = ? AND grade = '__ANY__' LIMIT 1",
                rs -> rs.next() ? List.of(rs.getInt(1)) : List.of(), employmentType);
        if (!rows.isEmpty()) return rows.get(0);
        List<Integer> fallback = jdbcTemplate.query(
                "SELECT notice_days FROM notice_rule WHERE employment_type = 'FULL_TIME' AND grade = '__ANY__' LIMIT 1",
                rs -> rs.next() ? List.of(rs.getInt(1)) : List.of());
        return fallback.isEmpty() ? 30 : fallback.get(0);
    }

    private Resignation mustGet(Long id) {
        return resignationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resignation not found: " + id));
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

    private static String coalesce(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return fallback;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
