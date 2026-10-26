package com.mentis.hrms.pm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.pm.dto.request.ClosureItemRequest;
import com.mentis.hrms.pm.dto.response.ClosureItemResponse;
import com.mentis.hrms.pm.dto.response.ClosureStatusResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.ApprovalRequest;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectClosureItem;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ApprovalRequestRepository;
import com.mentis.hrms.pm.repository.ProjectClosureItemRepository;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closure checklist + the rules that decide whether a project may be COMPLETED.
 * Never calls PmAccessService (callers check permissions; this is also safe from any thread).
 *
 * STEP 8 changes:
 *  - an EMPTY checklist no longer passes (at least one closure item is required)
 *  - the standard checklist is added automatically when a project becomes ACTIVE
 *  - lessons learned need 30 characters (was 10)
 *  - a client sign-off given BEFORE the project was reopened no longer counts
 *  - completion summary in the closure status
 *  - the team is told when a project is completed or reopened
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectClosureService {

    public static final String SIGN_OFF = "CLIENT_SIGN_OFF";
    private static final int LESSONS_MIN_CHARS = 30;
    private static final Set<ProjectStatus> SIGN_OFF_STATES =
            EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.AT_RISK, ProjectStatus.DELAYED);
    private static final Set<ProjectStatus> LOCKED =
            EnumSet.of(ProjectStatus.COMPLETED, ProjectStatus.ARCHIVED);
    private static final TaskStatus[] OPEN_STATUSES = {
            TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, TaskStatus.BLOCKED };
    private static final List<String> DEFAULT_ITEMS = List.of(
            "Final deliverables handed over",
            "All invoices raised",
            "Documentation archived",
            "Team retrospective held",
            "Access and licences revoked");

    private final ProjectClosureItemRepository itemRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final ApprovalRequestRepository approvalRepository;
    private final ActivityLogRepository activityLogRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectMemberRepository projectMemberRepository; // STEP 8
    private final NotificationEmitter notificationEmitter;         // STEP 8

    private record SignOff(String state, Long approvalId) { }

    // ═════════════════════════ rules (used by ProjectServiceImpl + ApprovalServiceImpl) ═════════════════════════

    /** Reasons the project cannot be completed yet. Empty list = ready. */
    @Transactional(readOnly = true)
    public List<String> blockers(Project p) {
        List<String> out = new ArrayList<>();

        long open = openTasks(p.getId());
        if (open > 0) out.add(open + " task(s) are still open");

        // STEP 8: an empty checklist must not pass
        long totalItems = itemRepository.countByProjectId(p.getId());
        if (totalItems == 0) {
            out.add("the closure checklist is empty (open Closure and click 'Add standard checklist', or add your own items)");
        } else {
            long openItems = itemRepository.countByProjectIdAndDoneFalse(p.getId());
            if (openItems > 0) out.add(openItems + " closure checklist item(s) are not done");
        }

        if (!lessonsWritten(p)) out.add("lessons learned have not been written (at least " + LESSONS_MIN_CHARS + " characters)");

        switch (signOff(p).state()) {
            case "NOT_REQUESTED" -> out.add("client sign-off has not been requested");
            case "PENDING" -> out.add("client sign-off is still waiting for approval");
            case "REJECTED" -> out.add("client sign-off was rejected; request it again");
            default -> { }
        }
        return out;
    }

    /** Shape + state rules for a CLIENT_SIGN_OFF approval request. Throws BusinessRuleException. */
    @Transactional(readOnly = true)
    public void validateSignOff(Long projectId, JsonNode payload) {
        Project p = find(projectId);
        if (p.getClientId() == null)
            throw new BusinessRuleException("This project has no client, so no client sign-off is needed");
        if (!SIGN_OFF_STATES.contains(p.getStatus()))
            throw new BusinessRuleException("Client sign-off can only be requested while the project is Active, At Risk or Delayed");
        long open = openTasks(projectId);
        if (open > 0)
            throw new BusinessRuleException("Finish all tasks before asking for client sign-off (" + open + " still open)");
        String contact = payload != null && payload.hasNonNull("clientContact")
                ? payload.get("clientContact").asText().trim() : "";
        if (contact.isEmpty())
            throw new BusinessRuleException("payload.clientContact is required for a CLIENT_SIGN_OFF (who at the client accepted the work)");
        if (contact.length() > 200)
            throw new BusinessRuleException("payload.clientContact is too long (max 200 characters)");
    }

    // ═════════════════════════ read ═════════════════════════

    @Transactional(readOnly = true)
    public List<ClosureItemResponse> list(Long projectId) {
        find(projectId);
        return itemRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClosureStatusResponse status(Long projectId) {
        Project p = find(projectId);
        List<ClosureItemResponse> items = itemRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(this::toResponse).collect(Collectors.toList());
        List<String> blockers = blockers(p);
        SignOff so = signOff(p);

        // STEP 8: numbers for the completion summary
        long tasksTotal = 0, tasksDone = 0;
        for (TaskStatus s : TaskStatus.values()) {
            long n = taskRepository.countByProjectIdAndStatus(projectId, s);
            tasksTotal += n;
            if (s == TaskStatus.DONE) tasksDone = n;
        }
        Long delayDays = (p.getCompletedAt() != null && p.getEndDate() != null)
                ? ChronoUnit.DAYS.between(p.getEndDate(), p.getCompletedAt().toLocalDate())
                : null;

        return ClosureStatusResponse.builder()
                .projectId(projectId)
                .projectStatus(p.getStatus() == null ? null : p.getStatus().name())
                .clientRequired(p.getClientId() != null)
                .signOffState(so.state()).signOffApprovalId(so.approvalId())
                .lessonsLearnedWritten(lessonsWritten(p))
                .openTasks(openTasks(projectId))
                .totalItems(items.size())
                .doneItems(items.stream().filter(ClosureItemResponse::isDone).count())
                .ready(blockers.isEmpty())
                .blockers(blockers)
                .items(items)
                // STEP 8
                .completedAt(p.getCompletedAt()).completedByName(nameOf(p.getCompletedBy()))
                .reopenedAt(p.getReopenedAt()).reopenedByName(nameOf(p.getReopenedBy()))
                .reopenReason(p.getReopenReason())
                .plannedEnd(p.getEndDate()).delayDays(delayDays)
                .tasksTotal(tasksTotal).tasksDone(tasksDone)
                .build();
    }

    // ═════════════════════════ write ═════════════════════════

    @Transactional
    public ClosureItemResponse add(Long projectId, String title, Long actorId) {
        findUnlocked(projectId);
        ProjectClosureItem saved = itemRepository.save(ProjectClosureItem.builder()
                .projectId(projectId).title(cleanTitle(title)).done(false).build());
        log(projectId, "CLOSURE_ITEM_ADDED", actorId, "added closure item: " + saved.getTitle());
        return toResponse(saved);
    }

    @Transactional
    public ClosureItemResponse update(Long projectId, Long itemId, ClosureItemRequest req, Long actorId) {
        findUnlocked(projectId);
        ProjectClosureItem it = findItem(projectId, itemId);
        if (req.getTitle() != null) it.setTitle(cleanTitle(req.getTitle()));
        if (req.getDone() != null && req.getDone() != it.isDone()) {
            boolean done = req.getDone();
            it.setDone(done);
            it.setDoneBy(done ? actorId : null);
            it.setDoneAt(done ? LocalDateTime.now() : null);
            log(projectId, done ? "CLOSURE_ITEM_DONE" : "CLOSURE_ITEM_REOPENED", actorId,
                    (done ? "completed" : "reopened") + " closure item: " + it.getTitle());
        }
        return toResponse(itemRepository.save(it));
    }

    @Transactional
    public void delete(Long projectId, Long itemId, Long actorId) {
        findUnlocked(projectId);
        ProjectClosureItem it = findItem(projectId, itemId);
        itemRepository.delete(it);
        log(projectId, "CLOSURE_ITEM_DELETED", actorId, "removed closure item: " + it.getTitle());
    }

    /** The "Add standard checklist" button. */
    @Transactional
    public List<ClosureItemResponse> seedDefaults(Long projectId, Long actorId) {
        findUnlocked(projectId);
        if (itemRepository.countByProjectId(projectId) > 0)
            throw new BusinessRuleException("This project already has closure items");
        for (String t : DEFAULT_ITEMS)
            itemRepository.save(ProjectClosureItem.builder().projectId(projectId).title(t).done(false).build());
        log(projectId, "CLOSURE_ITEM_ADDED", actorId, "added the standard closure checklist");
        return list(projectId);
    }

    /**
     * STEP 8: called when a project becomes ACTIVE. Adds the standard checklist, but does nothing
     * when the project already has closure items (so it is safe to call every time).
     */
    @Transactional
    public void ensureDefaults(Long projectId, Long actorId) {
        if (itemRepository.countByProjectId(projectId) > 0) return;
        for (String t : DEFAULT_ITEMS)
            itemRepository.save(ProjectClosureItem.builder().projectId(projectId).title(t).done(false).build());
        log(projectId, "CLOSURE_ITEM_ADDED", actorId, "added the standard closure checklist when the project started");
    }

    /** Called from ProjectServiceImpl.deleteProject (no foreign keys, so we clean up ourselves). */
    @Transactional
    public void deleteForProject(Long projectId) {
        itemRepository.deleteByProjectId(projectId);
    }

    // ═════════════════════════ STEP 8: tell the team ═════════════════════════

    /** Owner, managers, leads, members (this includes the person who asked for the project). */
    @Transactional(readOnly = true)
    public void notifyCompleted(Project p, Long actorId) {
        notifyAudience(p, actorId, "PROJECT_COMPLETED",
                "Project completed: " + p.getName(),
                "Closed by " + (actorId == null ? "the system" : nameOf(actorId)) + ". The project is now locked.");
    }

    @Transactional(readOnly = true)
    public void notifyReopened(Project p, Long actorId, String reason) {
        notifyAudience(p, actorId, "PROJECT_REOPENED",
                "Project reopened: " + p.getName(),
                "Reopened by " + (actorId == null ? "the system" : nameOf(actorId)) + ": " + reason);
    }

    private void notifyAudience(Project p, Long actorId, String type, String title, String msg) {
        Set<Long> audience = new LinkedHashSet<>();
        if (p.getOwnerId() != null) audience.add(p.getOwnerId());
        for (ProjectMember m : projectMemberRepository.findByProjectId(p.getId())) {
            if (m.getEmployeeId() != null) audience.add(m.getEmployeeId());
        }
        audience.remove(actorId);
        if (audience.isEmpty()) return;

        final Long projectId = p.getId();
        final String actorName = actorId == null ? "System" : nameOf(actorId);
        afterCommit(() -> {
            for (Long pk : audience) {
                try {
                    String code = employeeRepository.findById(pk).map(e -> e.getEmployeeId()).orElse(null);
                    if (code == null) continue;
                    notificationEmitter.emit(NotificationRequest.builder()
                            .recipientId(code).recipientType("EMPLOYEE")
                            .type(type).action(type)
                            .title(title).message(msg)
                            .projectId(projectId)
                            .actorId(actorId).actorName(actorName)
                            .priority("NORMAL").deepLink("/pm/04-projects")
                            .build());
                } catch (Exception e) {
                    log.warn("closure notify failed for employee {}: {}", pk, e.getMessage());
                }
            }
        });
    }

    /** Send the bells only after the database change is really saved. */
    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { r.run(); }
            });
        } else {
            r.run();
        }
    }

    // ═════════════════════════ helpers ═════════════════════════

    private long openTasks(Long projectId) {
        long n = 0;
        for (TaskStatus s : OPEN_STATUSES) n += taskRepository.countByProjectIdAndStatus(projectId, s);
        return n;
    }

    private boolean lessonsWritten(Project p) {
        return p.getLessonsLearned() != null && p.getLessonsLearned().trim().length() >= LESSONS_MIN_CHARS;
    }

    /**
     * Latest non-cancelled CLIENT_SIGN_OFF approval decides.
     * STEP 8: a sign-off that was requested BEFORE the project was reopened no longer counts,
     * so reopened work needs a fresh sign-off.
     */
    private SignOff signOff(Project p) {
        if (p.getClientId() == null) return new SignOff("NOT_REQUIRED", null);
        for (ApprovalRequest a : approvalRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(SIGN_OFF, p.getId())) {
            if ("CANCELLED".equals(a.getStatus())) continue;
            if (p.getReopenedAt() != null && a.getCreatedAt() != null
                    && a.getCreatedAt().isBefore(p.getReopenedAt())) {
                return new SignOff("NOT_REQUESTED", null);
            }
            return new SignOff(a.getStatus(), a.getId()); // PENDING | APPROVED | REJECTED
        }
        return new SignOff("NOT_REQUESTED", null);
    }

    private String cleanTitle(String title) {
        String t = title == null ? "" : title.trim();
        if (t.isEmpty()) throw new BusinessRuleException("Title is required");
        if (t.length() > 300) throw new BusinessRuleException("Title is too long (max 300 characters)");
        return t;
    }

    private Project find(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private Project findUnlocked(Long id) {
        Project p = find(id);
        if (LOCKED.contains(p.getStatus()))
            throw new BusinessRuleException("Reopen the project before changing its closure checklist");
        return p;
    }

    private ProjectClosureItem findItem(Long projectId, Long itemId) {
        ProjectClosureItem it = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Closure item not found: " + itemId));
        if (!projectId.equals(it.getProjectId()))
            throw new ResourceNotFoundException("Closure item not found: " + itemId);
        return it;
    }

    private ClosureItemResponse toResponse(ProjectClosureItem i) {
        return ClosureItemResponse.builder()
                .id(i.getId()).projectId(i.getProjectId()).title(i.getTitle())
                .done(i.isDone()).doneBy(i.getDoneBy()).doneByName(nameOf(i.getDoneBy()))
                .doneAt(i.getDoneAt()).createdAt(i.getCreatedAt())
                .build();
    }

    private String nameOf(Long pk) {
        if (pk == null) return null;
        return employeeRepository.findById(pk).map(e -> {
            String f = e.getFirstName() == null ? "" : e.getFirstName();
            String l = e.getLastName() == null ? "" : e.getLastName();
            String full = (f + " " + l).trim();
            return full.isEmpty() ? "Employee #" + pk : full;
        }).orElse("Employee #" + pk);
    }

    private void log(Long projectId, String action, Long actorId, String msg) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("PROJECT").entityId(projectId).action(action)
                    .actorId(actorId).actorName(actorId == null ? "System" : nameOf(actorId))
                    .message(msg).build());
        } catch (Exception e) {
            log.warn("closure activity log failed for project {}: {}", projectId, e.getMessage());
        }
    }
}