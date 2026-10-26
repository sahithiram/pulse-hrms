package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.*;
import com.mentis.hrms.pm.dto.response.*;
import com.mentis.hrms.pm.entity.*;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.mapper.TaskMapper;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.TaskService;
import com.mentis.hrms.pm.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.service.NotificationEmitter;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import com.mentis.hrms.pm.security.PmCurrentUserHolder;
import com.mentis.hrms.pm.security.PmUserProvider;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.service.TaskWorkflow;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskWorkflow;
import com.mentis.hrms.pm.service.TaskEventNotifier;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskChecklistItemRepository checklistItemRepository;
    private final TaskWatcherRepository taskWatcherRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final TaskMapper taskMapper;
    private final TaskTimeLogRepository taskTimeLogRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final com.mentis.hrms.pm.realtime.PmEventPublisher publisher;
    private final NotificationEmitter notificationEmitter;

    // ─── Phase 5 additions ───────────────────────────────────
    private final SprintRepository sprintRepository;
    private final MilestoneRepository milestoneRepository;

    // ─── Step 2.5 addition — block direct DONE while sign-off pending
    private final ApprovalRequestRepository approvalRequestRepository;

    // ─── Step 3.1 additions ──────────────────────────────────
    private final WorkloadService workloadService;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;

    // ─── Step 5 ───
    private final TaskEventNotifier taskEventNotifier;
    // ─── Step 4 ───
    private final PmAccessService pmAccessService;

    // ══════════════════════════════════════════════════════════
    // CORE CRUD
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest req, Long currentUserId) {
        if (!projectRepository.existsById(req.getProjectId())) {
            throw new ResourceNotFoundException("Project not found: " + req.getProjectId());
        }
        Long teamId = req.getTeamId();
        if (teamId == null) {
            teamId = projectRepository.findById(req.getProjectId())
                    .map(Project::getTeamId).orElse(null);
        }
        if (teamId != null && !teamRepository.existsById(teamId)) {
            throw new ResourceNotFoundException("Team not found: " + teamId);
        }

        Task task = taskMapper.toEntity(req, currentUserId);
        task.setTeamId(teamId);
        if (req.getSprintId() != null) {
            sprintRepository.findById(req.getSprintId()).ifPresent(sp -> {
                if (!Objects.equals(sp.getProjectId(), req.getProjectId()))
                    throw new BusinessRuleException("Sprint belongs to a different project");
                task.setSprintId(sp.getId());
            });
        }
        if (req.getMilestoneId() != null) {
            milestoneRepository.findById(req.getMilestoneId()).ifPresent(ms -> {
                if (!Objects.equals(ms.getProjectId(), req.getProjectId()))
                    throw new BusinessRuleException("Milestone belongs to a different project");
                task.setMilestoneId(ms.getId());
            });
        }
        if (req.getKeyResultId() != null) {
            task.setKeyResultId(req.getKeyResultId());
        }

        Task saved = taskRepository.save(task);
        if (req.getAssigneeIds() != null) {
            for (Long empId : req.getAssigneeIds()) {
                taskAssignmentRepository.save(TaskAssignment.builder()
                        .taskId(saved.getId())
                        .employeeId(empId)
                        .assignedBy(currentUserId)
                        .build());

                if (empId.equals(currentUserId)) continue;
                String recipientCode = resolveEmployeeCode(empId);
                if (recipientCode == null) continue;
                try {
                    String actorName = resolveEmployeeName(currentUserId);
                    String projectName = projectRepository.findById(saved.getProjectId())
                            .map(Project::getName).orElse(null);
                    notificationEmitter.emit(NotificationRequest.builder()
                            .recipientId(recipientCode).recipientType("EMPLOYEE")
                            .type("TASK_ASSIGNED").action("TASK_ASSIGNED")
                            .title("New task assigned: " + saved.getTitle())
                            .message(actorName + " assigned you to " + saved.getTitle()
                                    + (projectName != null ? " in " + projectName : ""))
                            .taskId(saved.getId())
                            .projectId(saved.getProjectId())
                            .actorId(currentUserId).actorName(actorName)
                            .priority(saved.getPriority() != null ? saved.getPriority().name() : "NORMAL")
                            .deepLink("/pm/03-my-work?task=" + saved.getId())
                            .build());
                } catch (Exception e) {
                    log.warn("create-task notify failed for emp {}: {}", empId, e.getMessage());
                }
            }
        }

        logActivity(saved.getId(), "CREATED", currentUserId, null, null,
                "Task created: " + saved.getTitle());

        rollUp(saved);
        publisher.publish("POST", "/api/pm/tasks/" + saved.getId(), String.valueOf(currentUserId));
        return toFullResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse updateTask(Long id, UpdateTaskRequest req, Long currentUserId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        // ─── Step 3.2E — snapshot before edits ───
        Map<String, String> before = snapshot(task);

        if (req.getTitle() != null && !req.getTitle().equals(task.getTitle())) task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getStatus() != null && req.getStatus() != task.getStatus()) {
            if (req.getStatus() == TaskStatus.BLOCKED)
                throw new BusinessRuleException("Use the status change (with a reason) to block a task");
            if (task.getStatus() == TaskStatus.IN_REVIEW && req.getStatus() == TaskStatus.IN_PROGRESS
                    && pmAccessService.isReviewRequired(task.getProjectId())
                    && pmAccessService.canReviewTask(currentUserId, id)
                    && !pmAccessService.isTaskAssignee(currentUserId, id))
                throw new BusinessRuleException(
                        "Use the status change (with a reason) to send a task back for changes");
            assertTransition(task, req.getStatus(), currentUserId);
            assertCanMoveTo(task, req.getStatus());
            trackBlocked(task, task.getStatus(), req.getStatus());
            task.setStatus(req.getStatus());
            if (taskRepository.countSubtasks(id) == 0) {
                task.setProgressPct(deriveProgress(req.getStatus()));
            }
        }
        if (req.getPriority() != null) task.setPriority(req.getPriority());
        if (req.getType() != null) task.setType(req.getType());

        // ─── Step 3.2C — clearable dates ───
        if (Boolean.TRUE.equals(req.getClearStartDate())) task.setStartDate(null);
        else if (req.getStartDate() != null) task.setStartDate(req.getStartDate());

        if (Boolean.TRUE.equals(req.getClearDueDate())) task.setDueDate(null);
        else if (req.getDueDate() != null) task.setDueDate(req.getDueDate());

        if (req.getEstimateHours() != null) task.setEstimateHours(req.getEstimateHours());
        if (req.getStoryPoints() != null) task.setStoryPoints(req.getStoryPoints());
        if (req.getProgressPct() != null) task.setProgressPct(req.getProgressPct());
        if (req.getTeamId() != null) {
            if (req.getTeamId() == 0L) task.setTeamId(null);
            else if (teamRepository.existsById(req.getTeamId())) task.setTeamId(req.getTeamId());
        }

        if (req.getSprintId() != null) {
            if (req.getSprintId() == 0L) task.setSprintId(null);
            else sprintRepository.findById(req.getSprintId()).ifPresent(sp -> {
                if (!Objects.equals(sp.getProjectId(), task.getProjectId()))
                    throw new BusinessRuleException("Sprint belongs to a different project");
                task.setSprintId(sp.getId());
            });
        }
        if (req.getMilestoneId() != null) {
            if (req.getMilestoneId() == 0L) task.setMilestoneId(null);
            else milestoneRepository.findById(req.getMilestoneId()).ifPresent(ms -> {
                if (!Objects.equals(ms.getProjectId(), task.getProjectId()))
                    throw new BusinessRuleException("Milestone belongs to a different project");
                task.setMilestoneId(ms.getId());
            });
        }
        if (req.getKeyResultId() != null) {
            if (req.getKeyResultId() == 0L) task.setKeyResultId(null);
            else task.setKeyResultId(req.getKeyResultId());
        }

        Task saved = taskRepository.save(task);
        rollUp(saved);

        // ─── Step 3.2E — field-level activity log ───
        notifyViaUpdate(before, saved, currentUserId);
        String msg = describeChanges(before, snapshot(saved));
        logActivity(id, "UPDATED", currentUserId, null, null,
                msg.length() > 450 ? msg.substring(0, 450) + "…" : msg);

        publisher.publish("PATCH", "/api/pm/tasks/" + saved.getId(), String.valueOf(currentUserId));
        return toFullResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse changeStatus(Long id, TaskStatusChangeRequest req, Long currentUserId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        TaskStatus oldStatus = task.getStatus();
        TaskStatus newStatus = req.getStatus();

        // ─── Step 3.2D — BLOCKED requires a reason ───
        if (newStatus == TaskStatus.BLOCKED) {
            String reason = req.getReason();
            if (reason == null || reason.isBlank())
                throw new BusinessRuleException("Please give a reason for blocking this task");
            taskCommentRepository.save(TaskComment.builder()
                    .taskId(id).authorId(currentUserId)
                    .authorName(resolveEmployeeName(currentUserId))
                    .body("⛔ Blocked: " + reason.trim())
                    .mentions("").edited(false).deleted(false).build());
        }

        assertTransition(task, newStatus, currentUserId);
        assertCanMoveTo(task, newStatus);

        // Step 4: a reviewer sending a task back must say what to change
        if (oldStatus == TaskStatus.IN_REVIEW && newStatus == TaskStatus.IN_PROGRESS
                && pmAccessService.isReviewRequired(task.getProjectId())
                && pmAccessService.canReviewTask(currentUserId, id)
                && !pmAccessService.isTaskAssignee(currentUserId, id)) {
            String why = req.getReason();
            if (why == null || why.isBlank())
                throw new BusinessRuleException("Please say what needs to change before sending this task back");
            taskCommentRepository.save(TaskComment.builder()
                    .taskId(id).authorId(currentUserId)
                    .authorName(resolveEmployeeName(currentUserId))
                    .body("↩ Sent back for changes: " + why.trim())
                    .mentions("").edited(false).deleted(false).build());
        }

        task.setStatus(newStatus);
        trackBlocked(task, oldStatus, newStatus);
        if (taskRepository.countSubtasks(id) == 0) {
            task.setProgressPct(deriveProgress(newStatus));
        }

        Task saved = taskRepository.save(task);
        logActivity(id, "STATUS_CHANGED", currentUserId,
                oldStatus != null ? oldStatus.name() : null,
                newStatus.name(),
                "Status: " + oldStatus + " → " + newStatus);
        rollUp(saved);
        publisher.publish("PATCH", "/api/pm/tasks/" + saved.getId(), String.valueOf(currentUserId));

        try {
            String actorName = resolveEmployeeName(currentUserId);
            java.util.Set<Long> audience = new java.util.LinkedHashSet<>();
            TaskEventNotifier.Plan plan =
                    taskEventNotifier.plan(saved, oldStatus, newStatus, currentUserId, req.getReason());
            taskAssignmentRepository.findByTaskId(id).forEach(a -> audience.add(a.getEmployeeId()));
            taskWatcherRepository.findByTaskId(id).forEach(w -> audience.add(w.getEmployeeId()));
            audience.remove(currentUserId);
            audience.removeAll(plan.covered());   // they already get a specific Step 5 bell
            plan.sendAfterCommit();

            for (Long empPk : audience) {
                String recipientCode = resolveEmployeeCode(empPk);
                if (recipientCode == null) continue;
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(recipientCode)
                        .recipientType("EMPLOYEE")
                        .type("CARD_MOVED")
                        .action("CARD_MOVED")
                        .title("Task moved to " + (newStatus != null ? newStatus.name() : ""))
                        .message(actorName + " moved " + task.getTitle()
                                + " from " + oldStatus + " to " + newStatus)
                        .taskId(id)
                        .projectId(task.getProjectId())
                        .actorId(currentUserId)
                        .actorName(actorName)
                        .priority("NORMAL")
                        .deepLink("/pm/06-tasks?view=board&open=" + id)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Failed to emit CARD_MOVED for {}: {}", id, e.getMessage());
        }
        return toFullResponse(saved);
    }

    // ─── Step 5 helpers ───
    private void trackBlocked(Task task, TaskStatus from, TaskStatus to) {
        if (to == TaskStatus.BLOCKED && from != TaskStatus.BLOCKED) {
            task.setBlockedSince(java.time.LocalDateTime.now());
            task.setEscalationLevel(0);
        } else if (from == TaskStatus.BLOCKED && to != TaskStatus.BLOCKED) {
            task.setBlockedSince(null);
            task.setEscalationLevel(0);
        }
    }

    /** Status can also change through PUT /tasks/{id}; send the same bells there. */
    private void notifyViaUpdate(Map<String, String> before, Task saved, Long actorId) {
        try {
            String b = before.get("status");
            TaskStatus old = (b == null || "null".equals(b)) ? null : TaskStatus.valueOf(b);
            if (saved.getStatus() != null && saved.getStatus() != old) {
                taskEventNotifier.plan(saved, old, saved.getStatus(), actorId, null).sendAfterCommit();
            }
        } catch (Exception e) {
            log.warn("notifyViaUpdate failed for task {}: {}", saved.getId(), e.getMessage());
        }
    }

    private int deriveProgress(TaskStatus s) {
        return switch (s) {
            case TODO -> 0;
            case IN_PROGRESS -> 50;
            case IN_REVIEW -> 80;
            case BLOCKED -> 30;
            case DONE -> 100;
        };
    }

    // ─── Step 3.1 — assignTask rewrite with capacity warnings ──
    @Override
    @Transactional
    public TaskResponse assignTask(Long id, AssignTaskRequest req, Long currentUserId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));

        Set<Long> oldIds = taskAssignmentRepository.findByTaskId(id).stream()
                .map(TaskAssignment::getEmployeeId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> newIds = new LinkedHashSet<>(
                req.getEmployeeIds() == null ? List.<Long>of() : req.getEmployeeIds());

        taskAssignmentRepository.deleteByTaskId(id);
        for (Long empId : newIds) {
            taskAssignmentRepository.save(TaskAssignment.builder()
                    .taskId(id).employeeId(empId).assignedBy(currentUserId).build());
        }

        Set<Long> added = new LinkedHashSet<>(newIds);
        added.removeAll(oldIds);
        Set<Long> removed = new LinkedHashSet<>(oldIds);
        removed.removeAll(newIds);

        String actorName = resolveEmployeeName(currentUserId);
        Long projectId = task.getProjectId();
        String projectName = projectId != null
                ? projectRepository.findById(projectId).map(Project::getName).orElse(null) : null;

        for (Long empId : added) {
            if (empId.equals(currentUserId)) continue;
            String code = resolveEmployeeCode(empId);
            if (code == null) continue;
            try {
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type("TASK_ASSIGNED").action("TASK_ASSIGNED")
                        .title("New task assigned: " + task.getTitle())
                        .message(actorName + " assigned you to " + task.getTitle()
                                + (projectName != null ? " in " + projectName : ""))
                        .taskId(id).projectId(projectId)
                        .actorId(currentUserId).actorName(actorName)
                        .priority(task.getPriority() != null ? task.getPriority().name() : "NORMAL")
                        .deepLink("/pm/03-my-work?task=" + id)
                        .build());
            } catch (Exception e) {
                log.warn("Failed to emit TASK_ASSIGNED for {}: {}", empId, e.getMessage());
            }
        }
        for (Long empId : removed) {
            if (empId.equals(currentUserId)) continue;
            String code = resolveEmployeeCode(empId);
            if (code == null) continue;
            try {
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type("TASK_UNASSIGNED").action("TASK_UNASSIGNED")
                        .title("Removed from task: " + task.getTitle())
                        .message(actorName + " removed you from " + task.getTitle())
                        .taskId(id).projectId(projectId)
                        .actorId(currentUserId).actorName(actorName)
                        .priority("NORMAL")
                        .deepLink("/pm/03-my-work")
                        .build());
            } catch (Exception e) {
                log.warn("Failed to emit TASK_UNASSIGNED for {}: {}", empId, e.getMessage());
            }
        }

        // capacity warnings (never blocks), counted AFTER the assignment is saved
        List<String> warnings = new ArrayList<>();

        // PHASE 2 — membership warnings (never block)
        Long checkTeamId = task.getTeamId() != null ? task.getTeamId()
                : projectRepository.findById(task.getProjectId()).map(Project::getTeamId).orElse(null);
        for (Long empId : added) {
            try {
                boolean inTeam = checkTeamId != null && teamMemberRepository.findByTeamId(checkTeamId).stream()
                        .anyMatch(tm -> tm.getEmployeeId().equals(empId));
                var pm = projectMemberRepository.findByProjectIdAndEmployeeId(task.getProjectId(), empId);
                if (!inTeam && pm.isEmpty())
                    warnings.add(resolveEmployeeName(empId)
                            + " is not in this project's team or project members");
                else if (pm.isPresent() && pm.get().getRole() == ProjectRole.VIEWER)
                    warnings.add(resolveEmployeeName(empId)
                            + " is a Viewer on this project and can't update tasks");
            } catch (Exception ex) {
                log.warn("membership warning failed for {}: {}", empId, ex.getMessage());
            }
        }
        LocalDate from = task.getStartDate() != null ? task.getStartDate() : LocalDate.now();
        LocalDate to = task.getDueDate() != null ? task.getDueDate() : from.plusDays(7);
        if (to.isBefore(from)) to = from.plusDays(7);
        for (Long empId : added) {
            try {
                String w = workloadService.capacityWarning(empId, from, to);
                if (w != null) warnings.add(w);
            } catch (Exception ex) {
                log.warn("capacityWarning failed for {}: {}", empId, ex.getMessage());
            }
        }

        logActivity(id, "ASSIGNED", currentUserId, null,
                String.valueOf(newIds.size()), "Assignees updated: " + newIds.size());
        publisher.publish("PATCH", "/api/pm/tasks/" + id, String.valueOf(currentUserId));

        TaskResponse res = toFullResponse(task);
        res.setAssignmentWarnings(warnings);
        return res;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskResponse getTask(Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        return toFullResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> listTasks(Long projectId, TaskStatus status,
                                                       String search, Pageable pageable) {
        Page<Task> page = taskRepository.searchFull(projectId, null, status, search, pageable);
        return PageResponse.of(page.map(this::toSummaryWithNames));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TaskSummaryResponse> listTasksByTeam(Long teamId, TaskStatus status,
                                                             String search, Pageable pageable) {
        Page<Task> page = taskRepository.searchFull(null, teamId, status, search, pageable);
        return PageResponse.of(page.map(this::toSummaryWithNames));
    }

    // ─── Step 3.2A — block delete when subtasks exist ───
    @Override
    @Transactional
    public void deleteTask(Long id, Long currentUserId) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + id));
        Long parentId = task.getParentTaskId();
        Long projectId = task.getProjectId();

        long subs = taskRepository.countSubtasks(id);
        if (subs > 0)
            throw new BusinessRuleException("Delete or move the " + subs + " subtask(s) first");

        taskAttachmentRepository.deleteByTaskId(id);
        taskTimeLogRepository.deleteByTaskId(id);
        taskAssignmentRepository.deleteByTaskId(id);
        taskWatcherRepository.deleteByTaskId(id);
        taskCommentRepository.deleteAll(taskCommentRepository.findByTaskIdAndDeletedFalseOrderByCreatedAtAsc(id));
        checklistItemRepository.deleteByTaskId(id);
        taskDependencyRepository.deleteByTaskId(id);
        taskDependencyRepository.deleteByDependsOnTaskId(id);
        taskRepository.deleteById(id);

        if (parentId != null) {
            taskRepository.findById(parentId).ifPresent(this::rollUp);
        } else if (projectId != null) {
            projectRepository.findById(projectId).ifPresent(p -> {
                p.setProgressPct(avg(taskRepository.findByProjectIdAndParentTaskIdIsNull(p.getId())));
                projectRepository.save(p);
            });
        }
        log.info("Task deleted id={}", id);
    }

    // ══════════════════════════════════════════════════════════
    // SUBTASKS
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listSubtasks(Long parentTaskId) {
        if (!taskRepository.existsById(parentTaskId))
            throw new ResourceNotFoundException("Parent not found: " + parentTaskId);
        return taskRepository.findSubtasks(parentTaskId).stream()
                .map(this::toSummaryWithNames)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TaskResponse createSubtask(Long parentTaskId, CreateTaskRequest req, Long currentUserId) {
        Task parent = taskRepository.findById(parentTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent not found: " + parentTaskId));
        if (req.getProjectId() == null) req.setProjectId(parent.getProjectId());
        if (req.getTeamId() == null) req.setTeamId(parent.getTeamId());
        req.setParentTaskId(parentTaskId);
        return createTask(req, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public TaskBoardMetaResponse getBoardMeta(Long taskId) {
        long subs = taskRepository.countSubtasks(taskId);
        long subsDone = taskRepository.countSubtasksByStatus(taskId, TaskStatus.DONE);
        long blockers = taskRepository.countOpenBlockers(taskId, TaskStatus.DONE);

        List<TaskChecklistItem> items = checklistItemRepository.findByTaskIdOrderBySortOrderAsc(taskId);
        long chkTotal = items.size();
        long chkDone = items.stream().filter(i -> Boolean.TRUE.equals(i.getDone())).count();

        long depCount = taskDependencyRepository.findByTaskId(taskId).size();

        return TaskBoardMetaResponse.builder()
                .taskId(taskId)
                .subtaskCount(subs).subtaskDone(subsDone)
                .checklistTotal(chkTotal).checklistDone(chkDone)
                .openBlockerCount(blockers)
                .dependencyCount(depCount)
                .build();
    }

    // ══════════════════════════════════════════════════════════
    // DEPENDENCY GRAPH
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public DependencyGraph getDependencyGraph(Long taskId) {
        if (!taskRepository.existsById(taskId))
            throw new ResourceNotFoundException("Task not found: " + taskId);

        List<DependencyEdge> blocks = taskRepository.findBlocked(taskId).stream()
                .map(t -> new DependencyEdge(
                        t.getId(), t.getTitle(),
                        t.getStatus() != null ? t.getStatus().name() : "TODO",
                        t.getPriority() != null ? t.getPriority().name() : "MEDIUM",
                        "BLOCKS"))
                .collect(Collectors.toList());

        List<DependencyEdge> blockedBy = taskRepository.findBlockers(taskId).stream()
                .map(t -> new DependencyEdge(
                        t.getId(), t.getTitle(),
                        t.getStatus() != null ? t.getStatus().name() : "TODO",
                        t.getPriority() != null ? t.getPriority().name() : "MEDIUM",
                        "BLOCKED_BY"))
                .collect(Collectors.toList());

        long openBlockers = taskRepository.countOpenBlockers(taskId, TaskStatus.DONE);

        return new DependencyGraph(blocks, blockedBy, openBlockers);
    }

    @Override
    @Transactional
    public void addDependencyRich(Long taskId, AddDependencyRequest req) {
        addDependency(taskId, req);
    }

    // ══════════════════════════════════════════════════════════
    // COMMENTS
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public CommentResponse addComment(Long taskId, CreateCommentRequest req, Long userId) {
        if (!taskRepository.existsById(taskId))
            throw new ResourceNotFoundException("Task not found: " + taskId);

        String authorName = resolveEmployeeName(userId);
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();
        if (u != null) authorName = u.fullName();

        String mentions = parseMentions(taskId, req.getBody());

        TaskComment saved = taskCommentRepository.save(TaskComment.builder()
                .taskId(taskId).authorId(userId)
                .authorName(authorName)
                .body(req.getBody())
                .mentions(mentions)
                .edited(false).deleted(false).build());

        logActivity(taskId, "COMMENTED", userId, null, null,
                mentions.isBlank() ? "Comment added" : "Comment added with mentions");
        publisher.publish("POST", "/api/pm/tasks/" + taskId + "/comments", String.valueOf(userId));

        if (!mentions.isBlank()) {
            Task task = taskRepository.findById(taskId).orElse(null);
            Long projectId = task != null ? task.getProjectId() : null;
            String actorName2 = resolveEmployeeName(userId);

            for (String idStr : mentions.split(",")) {
                try {
                    Long mentionPk = Long.parseLong(idStr.trim());
                    if (mentionPk.equals(userId)) continue;

                    String recipientCode = resolveEmployeeCode(mentionPk);
                    if (recipientCode == null) continue;

                    notificationEmitter.emit(NotificationRequest.builder()
                            .recipientId(recipientCode)
                            .recipientType("EMPLOYEE")
                            .type("TASK_MENTIONED")
                            .action("TASK_MENTIONED")
                            .title(actorName2 + " mentioned you")
                            .message("In " + (task != null ? task.getTitle() : "a task") + ": "
                                    + trimPreview(req.getBody(), 120))
                            .taskId(taskId)
                            .projectId(projectId)
                            .actorId(userId)
                            .actorName(actorName2)
                            .priority("HIGH")
                            .deepLink("/pm/03-my-work?task=" + taskId)
                            .build());
                } catch (Exception e) {
                    log.warn("Failed to emit TASK_MENTIONED for {}: {}", idStr, e.getMessage());
                }
            }
        }
        return toCommentResponse(saved);
    }

    // ─── Step 3.2B — include project owner + team members in mention pool ───
    private String parseMentions(Long taskId, String body) {
        if (body == null || body.isBlank()) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("@([A-Za-z0-9._]+)").matcher(body);
        java.util.Set<String> handles = new java.util.LinkedHashSet<>();
        while (m.find()) handles.add(m.group(1).toLowerCase());
        if (handles.isEmpty()) return "";

        Long projectId = taskRepository.findById(taskId)
                .map(Task::getProjectId).orElse(null);
        if (projectId == null) return "";

        java.util.Set<Long> memberIds = new java.util.HashSet<>();
        taskRepository.findByProjectId(projectId).forEach(t ->
                taskAssignmentRepository.findByTaskId(t.getId())
                        .forEach(a -> memberIds.add(a.getEmployeeId())));
        projectRepository.findById(projectId).ifPresent(p -> {
            if (p.getOwnerId() != null) memberIds.add(p.getOwnerId());
            if (p.getTeamId() != null)
                teamMemberRepository.findByTeamId(p.getTeamId())
                        .forEach(tm -> memberIds.add(tm.getEmployeeId()));
        });

        java.util.List<String> matched = new java.util.ArrayList<>();
        for (Long empId : memberIds) {
            String name = resolveEmployeeName(empId);
            if (name == null) continue;
            String normalized = name.toLowerCase().replaceAll("\\s+", "");
            String firstName = name.split("\\s+")[0].toLowerCase();
            if (handles.contains(normalized) || handles.contains(firstName)) {
                matched.add(String.valueOf(empId));
            }
        }
        return String.join(",", matched);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long taskId) {
        return taskCommentRepository.findByTaskIdAndDeletedFalseOrderByCreatedAtAsc(taskId)
                .stream().map(this::toCommentResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        TaskComment c = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        c.setDeleted(true);
        taskCommentRepository.save(c);
    }

    private CommentResponse toCommentResponse(TaskComment c) {
        return CommentResponse.builder()
                .id(c.getId()).taskId(c.getTaskId())
                .authorId(c.getAuthorId()).authorName(c.getAuthorName())
                .body(c.getBody()).mentions(c.getMentions())
                .edited(c.getEdited())
                .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
                .build();
    }

    // ══════════════════════════════════════════════════════════
    // CHECKLIST
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<ChecklistItemResponse> listChecklist(Long taskId) {
        List<TaskChecklistItem> all = checklistItemRepository.findByTaskIdOrderBySortOrderAsc(taskId);
        log.info("listChecklist taskId={} found {} rows", taskId, all.size());

        Map<Long, List<TaskChecklistItem>> byParent = new HashMap<>();
        for (TaskChecklistItem item : all) {
            Long key = item.getParentId();
            byParent.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        List<ChecklistItemResponse> roots = byParent.getOrDefault(null, List.of()).stream()
                .map(item -> buildTree(item, byParent))
                .collect(Collectors.toList());

        for (TaskChecklistItem item : all) {
            if (item.getParentId() != null && !all.stream().anyMatch(x -> x.getId().equals(item.getParentId()))) {
                roots.add(buildTree(item, byParent));
            }
        }

        return roots;
    }

    private ChecklistItemResponse buildTree(TaskChecklistItem item,
                                            Map<Long, List<TaskChecklistItem>> byParent) {
        List<ChecklistItemResponse> children = byParent.getOrDefault(item.getId(), List.of()).stream()
                .map(c -> buildTree(c, byParent))
                .collect(Collectors.toList());
        return ChecklistItemResponse.builder()
                .id(item.getId()).taskId(item.getTaskId()).parentId(item.getParentId())
                .title(item.getTitle()).done(item.getDone())
                .assigneeId(item.getAssigneeId()).sortOrder(item.getSortOrder())
                .createdAt(item.getCreatedAt()).children(children).build();
    }

    @Override
    @Transactional
    public ChecklistItemResponse addChecklistItem(Long taskId, AddChecklistRequest req, Long userId) {
        if (!taskRepository.existsById(taskId))
            throw new ResourceNotFoundException("Task not found: " + taskId);
        TaskChecklistItem saved = checklistItemRepository.save(TaskChecklistItem.builder()
                .taskId(taskId)
                .parentId(req.getParentId())
                .title(req.getTitle())
                .done(false)
                .assigneeId(req.getAssigneeId())
                .sortOrder(0)
                .build());
        log.info("Checklist item added id={} taskId={} title={}", saved.getId(), taskId, req.getTitle());
        logActivity(taskId, "CHECKLIST_ADDED", userId, null, null, "Checklist: " + req.getTitle());

        return ChecklistItemResponse.builder()
                .id(saved.getId()).taskId(saved.getTaskId()).parentId(saved.getParentId())
                .title(saved.getTitle()).done(saved.getDone())
                .assigneeId(saved.getAssigneeId()).sortOrder(saved.getSortOrder())
                .createdAt(saved.getCreatedAt()).children(new ArrayList<>()).build();
    }

    @Override
    @Transactional
    public ChecklistItemResponse toggleChecklistItem(Long itemId, Long userId) {
        TaskChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + itemId));
        item.setDone(!Boolean.TRUE.equals(item.getDone()));
        TaskChecklistItem saved = checklistItemRepository.save(item);
        logActivity(saved.getTaskId(), "CHECKLIST_TOGGLED", userId, null, null, "Checklist toggled");
        return ChecklistItemResponse.builder()
                .id(saved.getId()).taskId(saved.getTaskId()).parentId(saved.getParentId())
                .title(saved.getTitle()).done(saved.getDone())
                .assigneeId(saved.getAssigneeId()).sortOrder(saved.getSortOrder())
                .createdAt(saved.getCreatedAt()).children(new ArrayList<>()).build();
    }

    @Override
    @Transactional
    public void deleteChecklistItem(Long itemId, Long userId) {
        TaskChecklistItem item = checklistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + itemId));
        checklistItemRepository.delete(item);
        logActivity(item.getTaskId(), "CHECKLIST_DELETED", userId, null, null, "Checklist deleted");
    }

    // ══════════════════════════════════════════════════════════
    // WATCHERS / DEPENDENCIES / HISTORY
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public void addWatcher(Long taskId, Long employeeId) {
        if (!taskRepository.existsById(taskId)) throw new ResourceNotFoundException("Task not found: " + taskId);
        if (taskWatcherRepository.existsByTaskIdAndEmployeeId(taskId, employeeId)) return;
        taskWatcherRepository.save(TaskWatcher.builder().taskId(taskId).employeeId(employeeId).build());
        logActivity(taskId, "WATCHER_ADDED", employeeId, null, null, "Watcher added");
    }

    @Override
    @Transactional
    public void removeWatcher(Long taskId, Long employeeId) {
        taskWatcherRepository.deleteByTaskIdAndEmployeeId(taskId, employeeId);
        logActivity(taskId, "WATCHER_REMOVED", employeeId, null, null, "Watcher removed");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listWatchers(Long taskId) {
        return taskWatcherRepository.findByTaskId(taskId)
                .stream().map(TaskWatcher::getEmployeeId).collect(Collectors.toList());
    }

    // ─── Step 1.4 — addDependency with cycle guard ───
    @Override
    @Transactional
    public void addDependency(Long taskId, AddDependencyRequest req) {
        Long dependsOnId = req.getDependsOnTaskId();
        if (!taskRepository.existsById(taskId))
            throw new ResourceNotFoundException("Task not found: " + taskId);
        if (dependsOnId == null || !taskRepository.existsById(dependsOnId))
            throw new ResourceNotFoundException("Depends-on task not found: " + dependsOnId);
        if (taskId.equals(dependsOnId))
            throw new BusinessRuleException("A task cannot depend on itself");

        boolean duplicate = taskDependencyRepository.findByTaskId(taskId).stream()
                .anyMatch(d -> dependsOnId.equals(d.getDependsOnTaskId()));
        if (duplicate)
            throw new BusinessRuleException("This dependency already exists");

        if (wouldCreateCycle(taskId, dependsOnId))
            throw new BusinessRuleException(
                    "This would create a circular dependency: the other task already "
                            + "depends on this one, directly or through other tasks");

        TaskDependency dep = taskDependencyRepository.save(TaskDependency.builder()
                .taskId(taskId)
                .dependsOnTaskId(dependsOnId)
                .type(req.getType() != null ? req.getType() : "BLOCKED_BY")
                .build());
        log.info("Dependency added id={} task={} dependsOn={} type={}",
                dep.getId(), taskId, dependsOnId, dep.getType());
        logActivity(taskId, "DEPENDENCY_ADDED", null, null,
                String.valueOf(dependsOnId), "Dependency added");
    }

    /**
     * Edge direction in your tables: row(taskId = X, dependsOnTaskId = Y) means
     * "X is blocked by Y". Adding X -> Y is a cycle if Y can already reach X.
     */
    private boolean wouldCreateCycle(Long taskId, Long dependsOnId) {
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(dependsOnId);
        Set<Long> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            if (cur.equals(taskId)) return true;
            if (!seen.add(cur)) continue;
            for (TaskDependency d : taskDependencyRepository.findByTaskId(cur)) {
                stack.push(d.getDependsOnTaskId());
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void removeDependency(Long depId) {
        TaskDependency dep = taskDependencyRepository.findById(depId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency not found: " + depId));
        taskDependencyRepository.delete(dep);
        logActivity(dep.getTaskId(), "DEPENDENCY_REMOVED", null, null, null, "Dependency removed");
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskDependency> listDependencies(Long taskId) {
        return taskRepository.findAllInvolving(taskId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivityResponse> taskHistory(Long taskId) {
        return activityLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc("TASK", taskId)
                .stream().map(this::toActivityResponse).collect(Collectors.toList());
    }

    private ActivityResponse toActivityResponse(ActivityLog a) {
        return ActivityResponse.builder()
                .id(a.getId()).entityType(a.getEntityType()).entityId(a.getEntityId())
                .action(a.getAction()).actorId(a.getActorId()).actorName(a.getActorName())
                .oldValue(a.getOldValue()).newValue(a.getNewValue())
                .message(a.getMessage()).createdAt(a.getCreatedAt()).build();
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════

    private void logActivity(Long taskId, String action, Long actorId,
                             String oldVal, String newVal, String message) {
        try {
            String actorName = "System";
            PmUserProvider.PmUser u = PmCurrentUserHolder.get();
            if (u != null) actorName = u.fullName();
            else if (actorId != null) actorName = resolveEmployeeName(actorId);

            activityLogRepository.save(ActivityLog.builder()
                    .entityType("TASK").entityId(taskId).action(action).actorId(actorId)
                    .actorName(actorName)
                    .oldValue(oldVal).newValue(newVal).message(message).build());
        } catch (Exception e) {
            log.warn("Failed to log activity for task {}: {}", taskId, e.getMessage());
        }
    }

    // ─── Step 3.2E — snapshot + describeChanges ───
    private Map<String, String> snapshot(Task t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("title", String.valueOf(t.getTitle()));
        m.put("status", String.valueOf(t.getStatus()));
        m.put("priority", String.valueOf(t.getPriority()));
        m.put("startDate", String.valueOf(t.getStartDate()));
        m.put("dueDate", String.valueOf(t.getDueDate()));
        m.put("estimateHours", String.valueOf(t.getEstimateHours()));
        m.put("storyPoints", String.valueOf(t.getStoryPoints()));
        m.put("progressPct", String.valueOf(t.getProgressPct()));
        m.put("sprintId", String.valueOf(t.getSprintId()));
        m.put("milestoneId", String.valueOf(t.getMilestoneId()));
        m.put("teamId", String.valueOf(t.getTeamId()));
        return m;
    }

    private String describeChanges(Map<String, String> before, Map<String, String> after) {
        List<String> parts = new ArrayList<>();
        after.forEach((k, v) -> {
            String b = before.get(k);
            if (!Objects.equals(b, v)) parts.add(k + ": " + b + " → " + v);
        });
        return parts.isEmpty() ? "Task updated (no field changes)" : String.join("; ", parts);
    }

    private TaskResponse toFullResponse(Task task) {
        List<Long> ids = taskAssignmentRepository.findByTaskId(task.getId())
                .stream().map(TaskAssignment::getEmployeeId).collect(Collectors.toList());
        List<String> names = resolveEmployeeNames(ids);
        String teamName = task.getTeamId() != null
                ? teamRepository.findById(task.getTeamId()).map(Team::getName).orElse(null)
                : null;
        String projectName = task.getProjectId() != null
                ? projectRepository.findById(task.getProjectId()).map(Project::getName).orElse(null)
                : null;

        String sprintName = null;
        if (task.getSprintId() != null) {
            try {
                sprintName = sprintRepository.findById(task.getSprintId())
                        .map(Sprint::getName).orElse(null);
            } catch (Exception ex) {
                log.warn("Sprint {} missing for task {}: {}", task.getSprintId(), task.getId(), ex.getMessage());
            }
        }

        String milestoneName = null;
        if (task.getMilestoneId() != null) {
            try {
                milestoneName = milestoneRepository.findById(task.getMilestoneId())
                        .map(Milestone::getName).orElse(null);
            } catch (Exception ex) {
                log.warn("Milestone {} missing for task {}: {}", task.getMilestoneId(), task.getId(), ex.getMessage());
            }
        }

        TaskResponse res = taskMapper.toResponse(
                task,
                task.getTeamId(), teamName,
                task.getProjectId(), projectName,
                ids, names,
                task.getSprintId(), sprintName,
                task.getMilestoneId(), milestoneName
        );

        res.setSubtaskCount(taskRepository.countSubtasks(task.getId()));
        res.setSubtaskDone(taskRepository.countSubtasksByStatus(task.getId(), TaskStatus.DONE));
        res.setOpenBlockerCount(taskRepository.countOpenBlockers(task.getId(), TaskStatus.DONE));

        return res;
    }

    private TaskSummaryResponse toSummaryWithNames(Task t) {
        List<Long> ids = taskAssignmentRepository.findByTaskId(t.getId())
                .stream().map(TaskAssignment::getEmployeeId).collect(Collectors.toList());
        List<String> names = resolveEmployeeNames(ids);
        String teamName = t.getTeamId() != null
                ? teamRepository.findById(t.getTeamId()).map(Team::getName).orElse(null)
                : null;
        String projectName = t.getProjectId() != null
                ? projectRepository.findById(t.getProjectId()).map(Project::getName).orElse(null)
                : null;

        String sprintName = null;
        if (t.getSprintId() != null) {
            try {
                sprintName = sprintRepository.findById(t.getSprintId())
                        .map(Sprint::getName).orElse(null);
            } catch (Exception ex) { /* ignore */ }
        }

        String milestoneName = null;
        if (t.getMilestoneId() != null) {
            try {
                milestoneName = milestoneRepository.findById(t.getMilestoneId())
                        .map(Milestone::getName).orElse(null);
            } catch (Exception ex) { /* ignore */ }
        }

        TaskSummaryResponse summary = taskMapper.toSummary(
                t, projectName, teamName, ids, names,
                t.getSprintId(), sprintName,
                t.getMilestoneId(), milestoneName);

        summary.setStoryPoints(t.getStoryPoints());
        summary.setSubtaskCount(taskRepository.countSubtasks(t.getId()));
        summary.setSubtaskDone(taskRepository.countSubtasksByStatus(t.getId(), TaskStatus.DONE));
        summary.setOpenBlockerCount(taskRepository.countOpenBlockers(t.getId(), TaskStatus.DONE));

        return summary;
    }

    private List<String> resolveEmployeeNames(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<String> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            try {
                out.add(resolveEmployeeName(id));
            } catch (Exception ex) {
                out.add("Employee #" + id);
            }
        }
        return out;
    }

    // ─── Step 2.5 — block direct DONE when a sign-off is pending ───
    private void assertCanMoveTo(Task task, TaskStatus target) {
        if (target != TaskStatus.DONE) return;

        if (approvalRequestRepository.existsByEntityTypeAndEntityIdAndStatus("TASK", task.getId(), "PENDING"))
            throw new BusinessRuleException(
                    "This task is waiting for sign-off. It becomes DONE when the approver approves.");

        long blockers = taskRepository.countOpenBlockers(task.getId(), TaskStatus.DONE);
        if (blockers > 0)
            throw new BusinessRuleException("Can't mark DONE: " + blockers + " open blocker(s)");
        long subs = taskRepository.countSubtasks(task.getId());
        long done = taskRepository.countSubtasksByStatus(task.getId(), TaskStatus.DONE);
        if (subs > done)
            throw new BusinessRuleException("Can't mark DONE: " + (subs - done) + " subtask(s) still open");
        long openChecklist = checklistItemRepository.findByTaskIdOrderBySortOrderAsc(task.getId()).stream()
                .filter(i -> !Boolean.TRUE.equals(i.getDone())).count();
        if (openChecklist > 0)
            throw new BusinessRuleException("Can't mark DONE: " + openChecklist + " checklist item(s) still open");
    }
    /** Allowed-moves rule + "don't walk away from a pending sign-off". */
    /** Allowed-moves rule (incl. Step 4 review gate) + "don't walk away from a pending sign-off". */
    private void assertTransition(Task task, TaskStatus to, Long userId) {
        TaskStatus from = task.getStatus();
        if (from == to) return;
        boolean gate = pmAccessService.isReviewGateActive(userId, task.getId());
        if (!TaskWorkflow.allowed(from, to, gate))
            throw new BusinessRuleException("A task cannot move from " + TaskWorkflow.label(from)
                    + " to " + TaskWorkflow.label(to) + "." + TaskWorkflow.hint(from, to, gate));
        if (from == TaskStatus.IN_REVIEW && to != TaskStatus.DONE
                && approvalRequestRepository.existsByEntityTypeAndEntityIdAndStatus("TASK", task.getId(), "PENDING"))
            throw new BusinessRuleException(
                    "This task is waiting for sign-off. Cancel the approval request first.");
    }


    private void rollUp(Task t) {
        if (t.getParentTaskId() != null) {
            taskRepository.findById(t.getParentTaskId()).ifPresent(parent -> {
                parent.setProgressPct(avg(taskRepository.findSubtasks(parent.getId())));
                taskRepository.save(parent);
                rollUp(parent);
            });
            return;
        }
        projectRepository.findById(t.getProjectId()).ifPresent(p -> {
            p.setProgressPct(avg(taskRepository.findByProjectIdAndParentTaskIdIsNull(p.getId())));
            projectRepository.save(p);
        });
    }

    // ─── Step 2.6 — weighted progress ───
    private double weightOf(Task t) {
        Integer est = t.getEstimateHours();
        if (est != null && est > 0) return est;
        Integer sp = t.getStoryPoints();
        if (sp != null && sp > 0) return sp * 4.0;
        return 1.0;
    }

    private int avg(List<Task> l) {
        double w = 0, s = 0;
        for (Task t : l) {
            double x = weightOf(t);
            int p = t.getProgressPct() == null ? 0 : t.getProgressPct();
            w += x;
            s += x * p;
        }
        return w == 0 ? 0 : (int) Math.round(s / w);
    }

    private String resolveEmployeeName(Long id) {
        if (id == null) return null;
        try {
            return employeeRepository.findById(id)
                    .map(e -> {
                        try {
                            String first = e.getFirstName() == null ? "" : e.getFirstName();
                            String last  = e.getLastName()  == null ? "" : e.getLastName();
                            String full = (first + " " + last).trim();
                            if (!full.isEmpty()) return full;
                            if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank())
                                return e.getEmployeeName();
                            if (e.getEmail() != null) return e.getEmail();
                        } catch (Exception ignored) {
                        }
                        return "Employee #" + id;
                    })
                    .orElse("Employee #" + id);
        } catch (Exception ex) {
            log.warn("resolveEmployeeName failed for id={}: {}", id, ex.getMessage());
            return "Employee #" + id;
        }
    }

    private String resolveEmployeeCode(Long employeePk) {
        if (employeePk == null) return null;
        try {
            return employeeRepository.findById(employeePk)
                    .map(e -> e.getEmployeeId())
                    .orElse(null);
        } catch (Exception ex) {
            log.warn("resolveEmployeeCode failed for pk={}: {}", employeePk, ex.getMessage());
            return null;
        }
    }

    private String trimPreview(String s, int max) {
        if (s == null) return "";
        String cleaned = s.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "…";
    }
}