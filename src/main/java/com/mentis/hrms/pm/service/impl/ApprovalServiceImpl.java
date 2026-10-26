package com.mentis.hrms.pm.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.ChangeRequestPayload;
import com.mentis.hrms.pm.dto.request.CreateApprovalRequest;
import com.mentis.hrms.pm.dto.request.DecisionRequest;
import com.mentis.hrms.pm.dto.request.TaskStatusChangeRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.ApprovalResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.ApprovalRequest;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskComment;
import com.mentis.hrms.pm.entity.TimesheetWeek;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.enums.TimesheetStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ApprovalRequestRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskChecklistItemRepository;
import com.mentis.hrms.pm.repository.TaskCommentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TimesheetWeekRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ApprovalService;
import com.mentis.hrms.pm.service.ChangeRequestService;
import com.mentis.hrms.pm.service.ProjectClosureService;
import com.mentis.hrms.pm.service.ProjectFinanceService;
import com.mentis.hrms.pm.service.ProjectService;
import com.mentis.hrms.pm.service.TaskService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApprovalServiceImpl implements ApprovalService {

    private final ApprovalRequestRepository repo;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;
    private final NotificationEmitter notificationEmitter;

    private final ObjectMapper objectMapper;
    private final PmAccessService pmAccessService;
    private final TaskService taskService;
    private final TaskCommentRepository taskCommentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final TimesheetWeekRepository timesheetWeekRepository;
    private final ProjectFinanceService projectFinanceService;
    private final TaskChecklistItemRepository checklistItemRepository;

    // Phase 6
    private final ChangeRequestService changeRequestService;
    private final ProjectClosureService closureService;
    private final ProjectService projectService;

    // ═════════════════════════ create ═════════════════════════

    @Override
    @Transactional
    public ApprovalResponse create(CreateApprovalRequest req, Long userId) {
        if (req.getApproverId() == null)
            throw new BusinessRuleException("Approver is required");
        if (req.getApproverId().equals(userId))
            throw new BusinessRuleException("You cannot approve your own request");

        String type = req.getEntityType().toUpperCase();
        validateTarget(type, req.getEntityId(), req.getPayload(), userId);

        // ✅ CHANGED (a): enforce routing eligibility at create time
        if (!pmAccessService.isEligibleApprover(type, req.getEntityId(), req.getApproverId()))
            throw new BusinessRuleException(
                    "That person can't approve this kind of request. Budget, change-request and client "
                            + "sign-off approvals go to a Super Admin; project status changes to the owner, a "
                            + "manager or a Super Admin; task sign-off to a lead, manager, owner or Super Admin.");

        String title = resolveEntityTitle(type, req.getEntityId());

        ApprovalRequest ar = ApprovalRequest.builder()
                .entityType(type)
                .entityId(req.getEntityId())
                .entityTitle(title)
                .requestedBy(userId)
                .approverId(req.getApproverId())
                .status("PENDING")
                .reason(req.getReason())
                .payload(req.getPayload())
                .priority(req.getPriority() != null ? req.getPriority() : "NORMAL")
                .build();
        ApprovalRequest saved = repo.save(ar);

        // a sign-off request parks the task in IN_REVIEW
        if ("TASK".equals(type)) moveTaskToReview(saved.getEntityId(), userId);

        notifyEmployee(saved.getApproverId(), "APPROVAL_REQUESTED",
                "Approval needed: " + title,
                nameOf(userId) + " requested your approval",
                null, "TASK".equals(saved.getEntityType()) ? saved.getEntityId() : null,
                userId, saved.getPriority(),
                "/pm/02-inbox?tab=approvals&focus=" + saved.getId());

        if ("CHANGE_REQUEST".equals(type)) onChangeRequestCreated(saved, userId);
        if ("CLIENT_SIGN_OFF".equals(type))
            logProject(saved.getEntityId(), "CLIENT_SIGN_OFF_REQUESTED", userId, null, null,
                    "requested client sign-off: " + signOffText(saved));

        publisher.publish("POST", "/api/pm/approvals/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalResponse get(Long id) {
        return toResponse(repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalResponse> listMine(Long userId) {
        return repo.findByApproverIdAndStatusOrderByCreatedAtDesc(userId, "PENDING").stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalResponse> listRequested(Long userId) {
        return repo.findByRequestedByOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalResponse> listAll(String status) {
        List<ApprovalRequest> list = (status == null || status.isBlank())
                ? repo.findAll()
                : repo.findByStatusOrderByCreatedAtDesc(status);
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ═════════════════════════ decide / cancel ═════════════════════════

    @Override
    @Transactional
    public ApprovalResponse decide(Long id, DecisionRequest req, Long userId) {
        ApprovalRequest ar = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + id));
        if (!"PENDING".equalsIgnoreCase(ar.getStatus()))
            throw new BusinessRuleException("This request has already been " + ar.getStatus());
        if (!userId.equals(ar.getApproverId()))
            throw new BusinessRuleException("You are not the approver for this request");

        String decision = req.getDecision().toUpperCase();
        if (!"APPROVED".equals(decision) && !"REJECTED".equals(decision))
            throw new BusinessRuleException("Decision must be APPROVED or REJECTED");

        assertMayDecide(ar, userId);

        ar.setStatus(decision);
        ar.setDecisionNote(req.getNote());
        ar.setDecidedBy(userId);
        ar.setDecidedAt(LocalDateTime.now());
        ApprovalRequest saved = repo.save(ar);

        // apply the outcome in the same transaction (a rule failure rolls the decision back)
        if ("APPROVED".equals(decision)) applyApproved(saved, userId);
        else applyRejected(saved, userId);

        notifyEmployee(saved.getRequestedBy(), "APPROVAL_DECIDED",
                "Approval " + decision.toLowerCase() + ": " + saved.getEntityTitle(),
                req.getNote() != null ? req.getNote() : "",
                null, null, userId,
                "HIGH".equals(saved.getPriority()) ? "HIGH" : "NORMAL",
                "/pm/02-inbox?tab=approvals&focus=" + saved.getId());

        publisher.publish("POST", "/api/pm/approvals/" + id + "/decide", String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void cancel(Long id, Long userId) {
        ApprovalRequest ar = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + id));
        if (!userId.equals(ar.getRequestedBy()))
            throw new BusinessRuleException("Only the requester can cancel");
        if (!"PENDING".equalsIgnoreCase(ar.getStatus()))
            throw new BusinessRuleException("Only PENDING requests can be cancelled");
        ar.setStatus("CANCELLED");
        if ("TIMESHEET".equals(ar.getEntityType())) releaseTimesheet(ar.getEntityId());
        repo.save(ar);
        publisher.publish("POST", "/api/pm/approvals/" + id + "/cancel", String.valueOf(userId));
    }

    // ═════════════════════════ validation on create ═════════════════════════

    /** requesterId == the current web user, so PmAccessService checks are valid here. */
    private void validateTarget(String type, Long entityId, String payload, Long requesterId) {
        switch (type) {
            case "PROJECT" -> {
                if (!projectRepository.existsById(entityId))
                    throw new ResourceNotFoundException("Project not found: " + entityId);
                pmAccessService.requireCanManageProject(requesterId, entityId);
                JsonNode p = parse(payload);
                if (p == null || !p.hasNonNull("toStatus"))
                    throw new BusinessRuleException("payload.toStatus is required for a PROJECT approval");
                ProjectStatus target;
                try { target = ProjectStatus.valueOf(p.get("toStatus").asText().toUpperCase()); }
                catch (IllegalArgumentException e) {
                    throw new BusinessRuleException("Unknown project status: " + p.get("toStatus").asText());
                }
                if (target == ProjectStatus.COMPLETED) assertClosable(entityId);   // early, friendly message
            }
            case "BUDGET" -> {
                if (!projectRepository.existsById(entityId))
                    throw new ResourceNotFoundException("Project not found: " + entityId);
                pmAccessService.requireCanManageProject(requesterId, entityId);
                JsonNode p = parse(payload);
                if (p == null || !p.hasNonNull("newBudget"))
                    throw new BusinessRuleException("payload.newBudget is required for a BUDGET approval");
                try {
                    if (new BigDecimal(p.get("newBudget").asText()).signum() < 0)
                        throw new BusinessRuleException("Budget cannot be negative");
                } catch (NumberFormatException e) {
                    throw new BusinessRuleException("payload.newBudget must be a number");
                }
            }
            case "CHANGE_REQUEST" -> {
                if (!projectRepository.existsById(entityId))
                    throw new ResourceNotFoundException("Project not found: " + entityId);
                pmAccessService.requireCanManageProject(requesterId, entityId);
                changeRequestService.validate(entityId, parse(payload));
            }
            case "CLIENT_SIGN_OFF" -> {
                if (!projectRepository.existsById(entityId))
                    throw new ResourceNotFoundException("Project not found: " + entityId);
                pmAccessService.requireCanManageProject(requesterId, entityId);
                closureService.validateSignOff(entityId, parse(payload));
            }
            case "TASK" -> {
                Task t = taskRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + entityId));
                pmAccessService.requireCanEdit(requesterId, entityId);
                if (t.getStatus() == TaskStatus.DONE)
                    throw new BusinessRuleException("This task is already DONE");
                if (t.getStatus() != TaskStatus.IN_PROGRESS && t.getStatus() != TaskStatus.IN_REVIEW)
                    throw new BusinessRuleException("Start the task (move it to In Progress) before requesting sign-off");
                long openChecklist = checklistItemRepository.findByTaskIdOrderBySortOrderAsc(entityId).stream()
                        .filter(i -> !Boolean.TRUE.equals(i.getDone())).count();
                if (openChecklist > 0)
                    throw new BusinessRuleException("Finish the checklist first (" + openChecklist + " item(s) still open)");
            }
            case "TIMESHEET" -> {                       // restored from Phase 3
                TimesheetWeek w = timesheetWeekRepository.findById(entityId)
                        .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found: " + entityId));
                if (!w.getEmployeeId().equals(requesterId))
                    throw new BusinessRuleException("You can only request approval for your own timesheet");
                if (w.getStatus() != TimesheetStatus.SUBMITTED)
                    throw new BusinessRuleException("Timesheet is not in SUBMITTED state");
            }
            default -> { }
        }

        // ✅ CHANGED (b): project-level requester must be able to administer the project,
        // applied once before the duplicate check (covers all project-level types).
        boolean projectLevel = "PROJECT".equals(type) || "BUDGET".equals(type)
                || "CHANGE_REQUEST".equals(type) || "CLIENT_SIGN_OFF".equals(type);
        if (projectLevel) pmAccessService.requireCanAdminister(requesterId, entityId);

        if (repo.existsByEntityTypeAndEntityIdAndStatus(type, entityId, "PENDING"))
            throw new BusinessRuleException("A pending approval already exists for this item");
    }

    private JsonNode parse(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try { return objectMapper.readTree(payload); }
        catch (Exception e) { throw new BusinessRuleException("payload is not valid JSON"); }
    }

    private void moveTaskToReview(Long taskId, Long userId) {
        Task t = taskRepository.findById(taskId).orElse(null);
        if (t == null || t.getStatus() == TaskStatus.IN_REVIEW) return;
        TaskStatusChangeRequest r = new TaskStatusChangeRequest();
        r.setStatus(TaskStatus.IN_REVIEW);
        taskService.changeStatus(taskId, r, userId);
    }

    // ✅ CHANGED (c): whole method replaced
    /** approver == current web user at decide time. Same rule as at create time. */
    private void assertMayDecide(ApprovalRequest ar, Long userId) {
        if (!pmAccessService.isEligibleApprover(ar.getEntityType(), ar.getEntityId(), userId))
            throw new PmAccessDeniedException("You can't approve this kind of request for this project.");
    }

    // ═════════════════════════ apply outcome ═════════════════════════

    private void applyApproved(ApprovalRequest ar, Long actorId) {
        switch (ar.getEntityType()) {
            case "PROJECT" -> {
                JsonNode p = parse(ar.getPayload());
                if (p == null || !p.hasNonNull("toStatus")) return;   // old rows without payload: no-op
                ProjectStatus to = ProjectStatus.valueOf(p.get("toStatus").asText().toUpperCase());
                // same path as the normal status change: FLOW map + closure rules + activity log
                projectService.updateProject(ar.getEntityId(),
                        UpdateProjectRequest.builder().status(to).build(), actorId);
                publisher.publish("PATCH", "/api/pm/projects/" + ar.getEntityId(), String.valueOf(actorId));
            }
            case "BUDGET" -> {
                JsonNode p = parse(ar.getPayload());
                if (p == null || !p.hasNonNull("newBudget")) return;
                BigDecimal nb = new BigDecimal(p.get("newBudget").asText());
                Project pr = projectRepository.findById(ar.getEntityId()).orElse(null);
                if (pr == null) return;
                String old = pr.getBudgetTotal() != null ? pr.getBudgetTotal().toPlainString() : null;
                if (pr.getBaselinedAt() != null) {
                    // a baselined project: this is a change request (archives the old baseline)
                    ObjectNode cr = objectMapper.createObjectNode();
                    cr.put("newBudget", nb);
                    cr.put("reason", ar.getReason() != null && !ar.getReason().isBlank()
                            ? ar.getReason() : "Budget approval");
                    changeRequestService.apply(pr.getId(), cr, ar.getId(), actorId);
                } else {
                    pr.setBudgetTotal(nb);
                    projectRepository.save(pr);
                }
                logProject(pr.getId(), "BUDGET_CHANGED", actorId, old, nb.toPlainString(),
                        "Approved: budget " + old + " → " + nb.toPlainString());
                publisher.publish("PATCH", "/api/pm/projects/" + pr.getId(), String.valueOf(actorId));
            }
            case "CHANGE_REQUEST" -> {
                String summary = changeRequestService.apply(
                        ar.getEntityId(), parse(ar.getPayload()), ar.getId(), actorId);
                logProject(ar.getEntityId(), "CHANGE_REQUEST_APPROVED", actorId, null, null,
                        "Approved change request: " + summary);
                notifyStakeholders(ar, actorId, "CHANGE_REQUEST_APPROVED",
                        "Change request approved: " + ar.getEntityTitle(), summary);
                publisher.publish("PATCH", "/api/pm/projects/" + ar.getEntityId(), String.valueOf(actorId));
            }
            case "CLIENT_SIGN_OFF" -> {
                String txt = signOffText(ar);
                logProject(ar.getEntityId(), "CLIENT_SIGN_OFF_APPROVED", actorId, null, null,
                        "Client sign-off approved: " + txt);
                notifyStakeholders(ar, actorId, "CLIENT_SIGN_OFF_APPROVED",
                        "Client sign-off approved: " + ar.getEntityTitle(),
                        txt + " — the project can now be completed");
            }
            case "TASK" -> {
                TaskStatusChangeRequest r = new TaskStatusChangeRequest();
                r.setStatus(TaskStatus.DONE);
                taskService.changeStatus(ar.getEntityId(), r, actorId);
            }
            case "TIMESHEET" -> {
                decideTimesheet(ar, actorId, TimesheetStatus.APPROVED);
                timesheetWeekRepository.findById(ar.getEntityId()).ifPresent(w ->
                        projectFinanceService.recalculateForEmployeeWeek(w.getEmployeeId(), w.getWeekStart()));
            }
            default -> log.info("Approval {} ({}) approved; nothing to apply yet", ar.getId(), ar.getEntityType());
        }
    }

    private void applyRejected(ApprovalRequest ar, Long actorId) {
        String note = ar.getDecisionNote() != null && !ar.getDecisionNote().isBlank()
                ? ar.getDecisionNote() : "(no note)";

        if ("TIMESHEET".equals(ar.getEntityType())) {
            decideTimesheet(ar, actorId, TimesheetStatus.REJECTED);
            return;
        }
        if ("CHANGE_REQUEST".equals(ar.getEntityType())) {
            logProject(ar.getEntityId(), "CHANGE_REQUEST_REJECTED", actorId, null, null,
                    "Rejected change request: " + note);
            notifyStakeholders(ar, actorId, "CHANGE_REQUEST_REJECTED",
                    "Change request rejected: " + ar.getEntityTitle(), note);
            return;
        }
        if ("CLIENT_SIGN_OFF".equals(ar.getEntityType())) {
            logProject(ar.getEntityId(), "CLIENT_SIGN_OFF_REJECTED", actorId, null, null,
                    "Client sign-off rejected: " + note);
            notifyStakeholders(ar, actorId, "CLIENT_SIGN_OFF_REJECTED",
                    "Client sign-off rejected: " + ar.getEntityTitle(), note);
            return;
        }
        if (!"TASK".equals(ar.getEntityType())) return;   // PROJECT / BUDGET: nothing changes

        taskRepository.findById(ar.getEntityId()).ifPresent(t -> {
            if (t.getStatus() == TaskStatus.IN_REVIEW) {
                TaskStatusChangeRequest r = new TaskStatusChangeRequest();
                r.setStatus(TaskStatus.IN_PROGRESS);
                taskService.changeStatus(t.getId(), r, actorId);
            }
            taskCommentRepository.save(TaskComment.builder()
                    .taskId(t.getId()).authorId(actorId).authorName(nameOf(actorId))
                    .body("❌ Sign-off rejected: " + note)
                    .mentions("").edited(false).deleted(false).build());
        });
    }

    // ═════════════════════════ timesheet helpers (Phase 3) ═════════════════════════

    private void decideTimesheet(ApprovalRequest ar, Long actorId, TimesheetStatus to) {
        timesheetWeekRepository.findById(ar.getEntityId()).ifPresent(w -> {
            w.setStatus(to);
            w.setDecidedBy(actorId);
            w.setDecidedAt(LocalDateTime.now());
            String note = ar.getDecisionNote();
            w.setNote(note == null || note.isBlank() ? null
                    : (note.length() > 500 ? note.substring(0, 500) : note));
            timesheetWeekRepository.save(w);
        });
    }

    private void releaseTimesheet(Long weekId) {
        timesheetWeekRepository.findById(weekId).ifPresent(w -> {
            if (w.getStatus() == TimesheetStatus.SUBMITTED) {
                w.setStatus(TimesheetStatus.DRAFT);
                w.setApprovalId(null);
                w.setApproverId(null);
                w.setSubmittedAt(null);
                timesheetWeekRepository.save(w);
            }
        });
    }

    // ═════════════════════════ Phase 6 helpers ═════════════════════════

    private void assertClosable(Long projectId) {
        Project pr = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        List<String> blockers = closureService.blockers(pr);
        if (!blockers.isEmpty())
            throw new BusinessRuleException("Cannot complete the project: " + String.join("; ", blockers) + ".");
    }

    private String signOffText(ApprovalRequest ar) {
        JsonNode p = parse(ar.getPayload());
        String who = p != null && p.hasNonNull("clientContact") ? p.get("clientContact").asText() : "the client";
        return "accepted by " + who;
    }

    private void onChangeRequestCreated(ApprovalRequest ar, Long requesterId) {
        String summary = ChangeRequestPayload.from(parse(ar.getPayload())).describe();
        logProject(ar.getEntityId(), "CHANGE_REQUEST_CREATED", requesterId, null, null,
                "Change requested: " + summary);
        for (Long pk : changeRequestService.stakeholders(ar.getEntityId())) {
            if (pk.equals(requesterId) || pk.equals(ar.getApproverId())) continue;
            notifyEmployee(pk, "CHANGE_REQUEST_CREATED", "Change request: " + ar.getEntityTitle(),
                    nameOf(requesterId) + " requested " + summary,
                    ar.getEntityId(), null, requesterId, ar.getPriority(), "/pm/04-projects");
        }
    }

    /** Owner + team lead, excluding the decider and the requester (the requester gets APPROVAL_DECIDED). */
    private void notifyStakeholders(ApprovalRequest ar, Long actorId, String type, String title, String msg) {
        for (Long pk : changeRequestService.stakeholders(ar.getEntityId())) {
            if (pk.equals(actorId) || pk.equals(ar.getRequestedBy())) continue;
            notifyEmployee(pk, type, title, msg, ar.getEntityId(), null, actorId, "NORMAL", "/pm/04-projects");
        }
    }

    private void logProject(Long projectId, String action, Long actorId,
                            String oldV, String newV, String msg) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("PROJECT").entityId(projectId).action(action)
                    .actorId(actorId).actorName(nameOf(actorId))
                    .oldValue(oldV).newValue(newV).message(msg).build());
        } catch (Exception e) {
            log.warn("project activity log failed: {}", e.getMessage());
        }
    }

    // ═════════════════════════ titles / mapping / notify ═════════════════════════

    private String resolveEntityTitle(String entityType, Long entityId) {
        if (entityType == null || entityId == null) return "Unknown";
        return switch (entityType.toUpperCase()) {
            case "TASK" -> taskRepository.findById(entityId).map(Task::getTitle).orElse("Task #" + entityId);
            case "PROJECT" -> projectRepository.findById(entityId).map(Project::getName).orElse("Project #" + entityId);
            case "BUDGET" -> projectRepository.findById(entityId)
                    .map(p -> "Budget change: " + p.getName()).orElse("Budget #" + entityId);
            case "CHANGE_REQUEST" -> projectRepository.findById(entityId)
                    .map(p -> "Change request: " + p.getName()).orElse("Change request #" + entityId);
            case "CLIENT_SIGN_OFF" -> projectRepository.findById(entityId)
                    .map(p -> "Client sign-off: " + p.getName()).orElse("Client sign-off #" + entityId);
            case "TIMESHEET" -> timesheetWeekRepository.findById(entityId)
                    .map(w -> "Timesheet: " + nameOf(w.getEmployeeId()) + " · week of " + w.getWeekStart())
                    .orElse("Timesheet #" + entityId);
            default -> entityType + " #" + entityId;
        };
    }

    private ApprovalResponse toResponse(ApprovalRequest a) {
        return ApprovalResponse.builder()
                .id(a.getId())
                .entityType(a.getEntityType()).entityId(a.getEntityId()).entityTitle(a.getEntityTitle())
                .requestedBy(a.getRequestedBy()).requestedByName(nameOf(a.getRequestedBy()))
                .approverId(a.getApproverId()).approverName(nameOf(a.getApproverId()))
                .status(a.getStatus()).reason(a.getReason()).decisionNote(a.getDecisionNote())
                .decidedBy(a.getDecidedBy()).decidedByName(nameOf(a.getDecidedBy()))
                .decidedAt(a.getDecidedAt()).priority(a.getPriority())
                .payload(a.getPayload())
                .createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt())
                .build();
    }

    private String nameOf(Long pk) {
        if (pk == null) return null;
        try {
            return employeeRepository.findById(pk)
                    .map(e -> {
                        try {
                            String first = e.getFirstName() == null ? "" : e.getFirstName();
                            String last = e.getLastName() == null ? "" : e.getLastName();
                            String full = (first + " " + last).trim();
                            if (!full.isEmpty()) return full;
                            if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank())
                                return e.getEmployeeName();
                            if (e.getEmail() != null) return e.getEmail();
                        } catch (Exception ignored) { }
                        return "Employee #" + pk;
                    })
                    .orElse("Employee #" + pk);
        } catch (Exception ex) {
            log.warn("nameOf failed for pk={}: {}", pk, ex.getMessage());
            return "Employee #" + pk;
        }
    }

    private void notifyEmployee(Long empPk, String type, String title, String message,
                                Long projectId, Long taskId, Long actorPk, String priority,
                                String deepLink) {
        if (empPk == null) return;
        try {
            String code;
            try {
                code = employeeRepository.findById(empPk).map(Employee::getEmployeeId).orElse(null);
            } catch (Exception ex) {
                log.warn("notify: employee {} lookup failed: {}", empPk, ex.getMessage());
                code = null;
            }
            if (code == null) return;
            notificationEmitter.emit(NotificationRequest.builder()
                    .recipientId(code).recipientType("EMPLOYEE")
                    .type(type).action(type)
                    .title(title).message(message)
                    .projectId(projectId).taskId(taskId)
                    .actorId(actorPk).actorName(nameOf(actorPk))
                    .priority(priority).deepLink(deepLink)
                    .build());
        } catch (Exception e) {
            log.warn("approval notify failed: {}", e.getMessage());
        }
    }
}