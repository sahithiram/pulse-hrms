package com.mentis.hrms.service;

import com.mentis.hrms.model.ExitChecklist;
import com.mentis.hrms.model.ExitInterview;
import com.mentis.hrms.repository.ExitChecklistRepository;
import com.mentis.hrms.repository.ExitInterviewRepository;
import com.mentis.hrms.repository.ResignationRepository;
import com.mentis.hrms.util.LifecycleRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * REQ-8-08 — exit checklist (seeded from LifecycleRules.defaultExitChecklist()) + exit interview.
 * REQ-8-09 — last-working-day finalize: EXITED + login disabled (active/is_active=0 + ExitEnforcementFilter),
 *             pending leave approvals re-routed to the approver's manager (else flagged to HR),
 *             direct-report count surfaced as a MANAGER_REASSIGN task, audit + notifications.
 *             Idempotent: already-EXITED employees are not re-processed.
 * Leave encashment: DEBIT_ENCASHMENT ledger entries are posted ONLY when HR marks the LEAVE_ENCASHMENT
 * checklist task DONE — the calculation report is available beforehand via encashmentReport().
 */
@Service
public class ExitService {

    private static final Logger logger = LoggerFactory.getLogger(ExitService.class);

    @Autowired private ExitChecklistRepository checklistRepository;
    @Autowired private ExitInterviewRepository interviewRepository;
    @Autowired private ResignationRepository resignationRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LifecycleSupport support;
    @Autowired private AuditWriter auditWriter;

    @Autowired(required = false)
    private HrmsNotificationService notificationService;

    /** Idempotent — uq (employee_id, task): existing rows are left untouched. */
    @Transactional
    public List<ExitChecklist> instantiateChecklist(String employeeId, LocalDate lastWorkingDay, String actor) {
        List<ExitChecklist> created = new ArrayList<>();
        for (String[] seed : LifecycleRules.defaultExitChecklist()) {
            if (checklistRepository.findByEmployeeIdAndTask(employeeId, seed[0]).isPresent()) continue;
            ExitChecklist item = new ExitChecklist();
            item.setEmployeeId(employeeId);
            item.setTask(seed[0]);
            item.setOwner(seed[1]);
            item.setStatus("PENDING");
            item.setDueDate(lastWorkingDay);
            created.add(checklistRepository.save(item));
        }
        if (!created.isEmpty()) {
            auditWriter.write("EXIT_CHECKLIST_CREATED", "exit_checklist", employeeId,
                    AuditWriter.json("items", String.valueOf(created.size()),
                            "lastWorkingDay", String.valueOf(lastWorkingDay)), actor);
        }
        return checklistRepository.findByEmployeeIdOrderByTaskAsc(employeeId);
    }

    @Transactional
    public ExitChecklist markTask(String employeeId, String task, String status, String completedBy, String notes) {
        ExitChecklist item = checklistRepository.findByEmployeeIdAndTask(employeeId, task)
                .orElseThrow(() -> new IllegalArgumentException("Exit checklist task not found: " + task + " for " + employeeId));
        if (!("DONE".equals(status) || "NA".equals(status) || "PENDING".equals(status))) {
            throw new IllegalArgumentException("status must be PENDING|DONE|NA");
        }
        item.setStatus(status);
        item.setNotes(notes);
        if ("DONE".equals(status) || "NA".equals(status)) {
            item.setCompletedAt(LocalDateTime.now());
            item.setCompletedBy(completedBy);
        }
        item = checklistRepository.save(item);
        auditWriter.write("EXIT_TASK_" + status, "exit_checklist", String.valueOf(item.getId()),
                AuditWriter.json("employee", employeeId, "task", task, "notes", notes == null ? "" : notes), completedBy);
        if ("LEAVE_ENCASHMENT".equals(task) && "DONE".equals(status)) {
            postEncashmentLedger(employeeId, item.getId(), completedBy);
        }
        return item;
    }

    /** Per-type encashable balance: min(balance, encashment_cap when set). Read-only report for HR. */
    public List<Map<String, Object>> encashmentReport(String employeeId) {
        return jdbcTemplate.queryForList(
                "SELECT p.leave_type_id, t.code, t.name, " +
                "COALESCE(SUM(l.amount), 0) AS balance, " +
                "p.encashment_cap AS cap, " +
                "CASE WHEN p.encashment_cap IS NULL THEN COALESCE(SUM(l.amount), 0) " +
                "     ELSE LEAST(COALESCE(SUM(l.amount), 0), p.encashment_cap) END AS encashable_days " +
                "FROM leave_policy p JOIN leave_type t ON t.id = p.leave_type_id " +
                "LEFT JOIN leave_ledger l ON l.leave_type_id = p.leave_type_id AND l.employee_id = ? " +
                "WHERE p.encashable = 1 AND t.is_active = 1 " +
                "GROUP BY p.leave_type_id, t.code, t.name, p.encashment_cap", employeeId);
    }

    /** Posts DEBIT_ENCASHMENT for the encashable amounts; idempotent via ledger unique key. */
    @Transactional
    public int postEncashmentLedger(String employeeId, Long referenceId, String actor) {
        int posted = 0;
        LocalDate today = LocalDate.now();
        for (Map<String, Object> row : encashmentReport(employeeId)) {
            BigDecimal encashable = (BigDecimal) row.get("encashable_days");
            if (encashable == null || encashable.signum() <= 0) continue;
            Number leaveTypeId = (Number) row.get("leave_type_id");
            // DEBIT entries are negative per Sprint-4 sign convention (+credit / -debit)
            int rows = jdbcTemplate.update(
                    "INSERT IGNORE INTO leave_ledger (employee_id, leave_type_id, entry_date, entry_type, amount, " +
                    "reference_type, reference_id, remarks, created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                    employeeId, leaveTypeId.longValue(), today, "DEBIT_ENCASHMENT",
                    encashable.negate(), "exit_encashment", String.valueOf(referenceId),
                    "Leave encashment on exit (" + row.get("code") + ")", actor);
            posted += rows;
        }
        if (posted > 0) {
            auditWriter.write("LEAVE_ENCASHMENT_POSTED", "leave_ledger", employeeId,
                    AuditWriter.json("entries", String.valueOf(posted)), actor);
        }
        return posted;
    }

    @Transactional
    public ExitInterview submitExitInterview(String employeeId, String formJson, String actor) {
        ExitInterview interview = interviewRepository.findByEmployeeId(employeeId)
                .orElseGet(() -> {
                    ExitInterview i = new ExitInterview();
                    i.setEmployeeId(employeeId);
                    return i;
                });
        interview.setFormJson(formJson);
        interview.setSubmittedAt(LocalDateTime.now());
        interview = interviewRepository.save(interview);
        auditWriter.write("EXIT_INTERVIEW_SUBMITTED", "exit_interview", employeeId,
                AuditWriter.json("employee", employeeId), actor);
        return interview;
    }

    public Optional<ExitInterview> exitInterview(String employeeId) {
        return interviewRepository.findByEmployeeId(employeeId);
    }

    /**
     * Finalize an exit. IDEMPOTENT — safe to run twice (nightly job + manual trigger + resignation completion).
     * Sets EXITED, disables login (active/is_active=0; ExitEnforcementFilter invalidates live sessions),
     * re-routes pending leave approvals, flags direct reports, flips resignation to COMPLETED, audits.
     */
    @Transactional
    public Map<String, Object> finalizeExit(String employeeId, String exitReason, String actor) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> emp = support.employeeByEmployeeId(employeeId);
        if (emp == null) throw new IllegalArgumentException("Employee not found: " + employeeId);

        String status = String.valueOf(emp.get("employment_status"));
        if ("EXITED".equals(status)) {
            result.put("finalized", false);
            result.put("reason", "already EXITED");
            return result;
        }
        Long pk = ((Number) emp.get("id")).longValue();
        LocalDate exitDate = resolveAcceptedLastDay(employeeId);
        if (exitDate == null) exitDate = LocalDate.now();

        // 1) Identity + access cut
        support.setExited(pk, exitDate, exitReason);
        support.jobHistory(pk, "EXIT_" + exitReason, exitDate, actor);

        // 2) Re-route pending leave approvals (steps first, then the request pointer)
        String approverEmployeeId = (String) emp.get("employee_id");
        String replacementApprover = support.employeeIdOfPk(toLong(emp.get("manager_employee_id")));
        int stepsRerouted = 0, requestsRerouted = 0;
        if (replacementApprover != null && !replacementApprover.equals(approverEmployeeId)) {
            stepsRerouted = jdbcTemplate.update(
                    "UPDATE leave_approval_step SET approver_id = ? WHERE approver_id = ? AND status = 'PENDING'",
                    replacementApprover, approverEmployeeId);
            requestsRerouted = jdbcTemplate.update(
                    "UPDATE leave_requests SET current_approver_id = ? WHERE current_approver_id = ? AND status = 'PENDING'",
                    replacementApprover, approverEmployeeId);
        } else {
            int orphanSteps = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM leave_approval_step WHERE approver_id = ? AND status = 'PENDING'",
                    Integer.class, approverEmployeeId);
            if (orphanSteps > 0) {
                notify("HR", "GENERIC",
                        Map.of("title", "Pending leave approvals need an owner",
                               "body", orphanSteps + " pending approval step(s) were assigned to exiting employee " + employeeId + " with no resolvable manager."),
                        "/leave/approvals", "ORPHAN-APPROVALS-" + employeeId);
            }
        }

        // 3) Direct reports → manager-reassignment task for HR (exit_checklist MANAGER_REASSIGN)
        int reports = support.directReportCount(pk);
        if (reports > 0) {
            checklistRepository.findByEmployeeIdAndTask(employeeId, "MANAGER_REASSIGN").ifPresent(item -> {
                item.setNotes("Reassign manager for " + reports + " direct report(s) of " + employeeId);
                item.setDueDate(LocalDate.now().plusDays(7));
                checklistRepository.save(item);
            });
            notify("HR", "MANAGER_REASSIGNMENT_NEEDED",
                    Map.of("employee", employeeId),
                    "/employees?managerReassignment=" + employeeId, "MGRREASSIGN-" + employeeId + "-" + exitDate);
        }

        // 4) Resignation → COMPLETED
        resignationRepository.findByEmployeeIdAndStatus(employeeId, "ACCEPTED").ifPresent(r -> {
            r.setStatus("COMPLETED");
            resignationRepository.save(r);
        });

        // 5) Checklist must exist for this employee (defensive; normally created at accept())
        instantiateChecklist(employeeId, exitDate, actor);

        auditWriter.write("EMPLOYEE_EXITED", "employee", employeeId,
                AuditWriter.json("exitDate", String.valueOf(exitDate), "exitReason", exitReason,
                        "approvalStepsRerouted", String.valueOf(stepsRerouted),
                        "leaveRequestsRerouted", String.valueOf(requestsRerouted),
                        "directReports", String.valueOf(reports)), actor);
        notify("HR", "GENERIC",
                Map.of("title", "Employee exited",
                       "body", employeeId + " exited on " + exitDate + " (" + exitReason + "). Access disabled; "
                               + stepsRerouted + " approval step(s) re-routed; " + reports + " direct report(s) flagged."),
                "/employees/" + employeeId, "EXIT-" + employeeId + "-" + exitDate);

        result.put("finalized", true);
        result.put("exitDate", exitDate.toString());
        result.put("approvalStepsRerouted", stepsRerouted);
        result.put("leaveRequestsRerouted", requestsRerouted);
        result.put("directReports", reports);
        return result;
    }

    public List<ExitChecklist> checklist(String employeeId) {
        return checklistRepository.findByEmployeeIdOrderByTaskAsc(employeeId);
    }

    public long checklistPending(String employeeId) {
        return checklistRepository.countByEmployeeIdAndStatusNot(employeeId, "DONE");
    }

    private LocalDate resolveAcceptedLastDay(String employeeId) {
        return resignationRepository.findByEmployeeIdAndStatus(employeeId, "ACCEPTED")
                .map(r -> r.getAcceptedLastDay())
                .orElse(null);
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

    private static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
