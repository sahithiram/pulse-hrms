package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.HandoverRequest;
import com.mentis.hrms.pm.dto.response.HandoverResponse;
import com.mentis.hrms.pm.entity.*;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.HandoverService;
import com.mentis.hrms.pm.service.ProjectMemberService;
import com.mentis.hrms.pm.service.TaskEventNotifier;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.service.WorkloadService;
import java.time.LocalDate;
import java.util.*;
import com.mentis.hrms.pm.service.PmParticipantService;
import com.mentis.hrms.pm.security.PmAccessService;

@Service
@RequiredArgsConstructor
@Slf4j
public class HandoverServiceImpl implements HandoverService {

    private final TaskAssignmentRepository assignmentRepo;
    private final TaskRepository taskRepo;
    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final ProjectMemberService projectMemberService;
    private final TeamRepository teamRepo;
    private final ActivityLogRepository activityRepo;
    private final EmployeeRepository employeeRepo;
    private final TaskEventNotifier notifier;
    private final PmAccessService access;
    private record Pair(TaskAssignment a, Task t) {}
    private final TeamMemberRepository teamMemberRepository;
    private final WorkloadService workloadService;
    // ───────────── preview ─────────────
    private final PmParticipantService participants;

    @Override
    @Transactional(readOnly = true)
    public List<HandoverResponse.Row> openTasks(Long employeePk, Long projectId) {
        Map<Long, String> names = new HashMap<>();
        List<HandoverResponse.Row> out = new ArrayList<>();
        for (Pair p : openAssignments(employeePk, projectId, null)) out.add(row(p.t(), names));
        return out;
    }

    // ───────────── bulk reassign ─────────────

    @Override
    @Transactional
    public HandoverResponse reassign(HandoverRequest req, Long actorPk) {
        Long fromId = req.getFromEmployeeId();
        Long toId = req.getToEmployeeId();
        if (fromId.equals(toId))
            throw new BusinessRuleException("Pick a different person to hand the work over to");
        Employee to = employeeRepo.findById(toId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + toId));
        employeeRepo.findById(fromId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + fromId));

        Set<Long> wanted = req.getTaskIds() == null ? null : new HashSet<>(req.getTaskIds());
        String actorName = notifier.nameOf(actorPk);
        String fromName = notifier.nameOf(fromId);
        String toName = notifier.nameOf(toId);

        Map<Long, String> names = new HashMap<>();
        List<HandoverResponse.Row> moved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Pair p : openAssignments(fromId, req.getProjectId(), wanted)) {
            Task t = p.t();
            if (access.isProjectLocked(t.getProjectId())) {
                skipped.add("\"" + t.getTitle() + "\": project " + projectName(t.getProjectId(), names)
                        + " is completed or archived, so its tasks are locked");
                continue;
            }
            // new person must belong to the task's project (project-scoped access)
            if (t.getProjectId() != null && !isParticipant(t, toId)) {
                skipped.add("\"" + t.getTitle() + "\": " + toName
                        + " is not in the team or members of " + projectName(t.getProjectId(), names)
                        + " (or is a Viewer)");
                continue;
            }
            moveAssignment(p.a(), t, toId, actorPk, actorName, fromName, toName,
                    "Handover: " + fromName + " → " + toName
                            + (req.getReason() == null || req.getReason().isBlank() ? "" : " (" + req.getReason() + ")"));
            moved.add(row(t, names));
        }

        // optionally remove from the project once nothing open is left
        boolean removed = false;
        if (req.isRemoveFromProject() && req.getProjectId() != null) {
            Long pid = req.getProjectId();
            Project proj = projectRepo.findById(pid).orElse(null);
            Optional<ProjectMember> mem = memberRepo.findByProjectIdAndEmployeeId(pid, fromId);
            if (mem.isPresent()) {
                if (proj != null && fromId.equals(proj.getOwnerId())) {
                    skipped.add("Not removed: " + fromName + " is the project owner. Change the owner first.");
                } else if (!openAssignments(fromId, pid, null).isEmpty()) {
                    skipped.add("Not removed: " + fromName + " still has open tasks on this project.");
                } else {
                    projectMemberService.remove(pid, mem.get().getId(), actorPk);
                    removed = true;
                }
            }
        }
        if (!moved.isEmpty()) {
            try {
                String w = workloadService.capacityWarning(toId, LocalDate.now(), LocalDate.now().plusDays(14));
                if (w != null) skipped.add("Heads-up: " + w);
            } catch (Exception ignored) { }
        }
        // one summary bell to the new owner, sent only after commit
        if (!moved.isEmpty()) {
            String code = notifier.codeOf(toId);
            if (code != null) {
                List<NotificationRequest> bells = new ArrayList<>();
                bells.add(notifier.build(code, "TASK_HANDED_OVER",
                        moved.size() + " task(s) handed over to you",
                        actorName + " handed over " + moved.size() + " task(s) from " + fromName,
                        "HIGH", null, req.getProjectId(), "/pm/06-tasks?view=board",
                        actorPk, actorName));
                sendAfterCommit(bells);
            }
        }

        return HandoverResponse.builder()
                .moved(moved.size()).memberRemoved(removed)
                .tasks(moved).skipped(skipped).build();
    }

    @Override
    @Transactional   // joins the caller's transaction; one failed move never throws, it returns a reason
    public String moveOne(Long taskId, Long fromId, Long toId, Long actorPk, String reason) {
        if (fromId == null || toId == null || fromId.equals(toId)) return "Pick a different person.";
        Task t = taskRepo.findById(taskId).orElse(null);
        if (t == null) return "Task #" + taskId + " no longer exists.";
        String label = "\"" + t.getTitle() + "\": ";
        if (!isOpen(t)) return label + "the task is no longer open.";
        if (access.isProjectLocked(t.getProjectId()))
            return label + "its project is completed or archived, so its tasks are locked.";
        if (!isParticipant(t, toId))
            return label + notifier.nameOf(toId) + " is not in the team or members of the project (or is a Viewer).";

        List<TaskAssignment> current = assignmentRepo.findByTaskId(taskId);
        TaskAssignment a = current.stream().filter(x -> fromId.equals(x.getEmployeeId())).findFirst().orElse(null);
        if (a == null) return label + notifier.nameOf(fromId) + " is no longer assigned to it.";
        if (current.stream().anyMatch(x -> toId.equals(x.getEmployeeId())))
            return label + notifier.nameOf(toId) + " is already on this task.";

        String actorName = notifier.nameOf(actorPk);
        String fromName = notifier.nameOf(fromId);
        String toName = notifier.nameOf(toId);
        moveAssignment(a, t, toId, actorPk, actorName, fromName, toName,
                (reason == null || reason.isBlank() ? "Reassigned" : reason) + ": " + fromName + " → " + toName);
        return null;
    }

    private boolean isParticipant(Task t, Long empPk) {
        return participants.isParticipant(t, empPk);
    }

    // ───────────── replace team lead ─────────────

    @Override
    @Transactional
    public void replaceTeamLead(Long teamId, Long newLeadPk, Long actorPk) {
        Team team = teamRepo.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        employeeRepo.findById(newLeadPk)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + newLeadPk));
        Long oldLead = team.getLeadEmployeeId();
        if (Objects.equals(oldLead, newLeadPk))
            throw new BusinessRuleException("That person is already the team lead");

        team.setLeadEmployeeId(newLeadPk);
        teamRepo.save(team);

        String actorName = notifier.nameOf(actorPk);
        activityRepo.save(ActivityLog.builder()
                .entityType("TEAM").entityId(teamId).action("LEAD_CHANGED")
                .actorId(actorPk).actorName(actorName)
                .oldValue(oldLead == null ? null : notifier.nameOf(oldLead))
                .newValue(notifier.nameOf(newLeadPk))
                .message("Team lead changed for " + team.getName())
                .build());

        List<NotificationRequest> bells = new ArrayList<>();
        String newCode = notifier.codeOf(newLeadPk);
        if (newCode != null && !newLeadPk.equals(actorPk))
            bells.add(notifier.build(newCode, "TEAM_LEAD_ASSIGNED",
                    "You are now team lead: " + team.getName(),
                    actorName + " made you lead of " + team.getName(),
                    "HIGH", null, null, "/pm/04-projects", actorPk, actorName));
        if (oldLead != null && !oldLead.equals(actorPk)) {
            String oldCode = notifier.codeOf(oldLead);
            if (oldCode != null)
                bells.add(notifier.build(oldCode, "TEAM_LEAD_REMOVED",
                        "You are no longer team lead: " + team.getName(),
                        actorName + " replaced you as lead of " + team.getName(),
                        "NORMAL", null, null, "/pm/04-projects", actorPk, actorName));
        }
        sendAfterCommit(bells);
    }

    // ───────────── helpers ─────────────

    /** One assignment from -> to, plus its ActivityLog row. Shared by handover and auto-balance. */
    private void moveAssignment(TaskAssignment a, Task t, Long toId, Long actorPk, String actorName,
                                String fromName, String toName, String message) {
        boolean alreadyOnTask = assignmentRepo.findByTaskId(t.getId()).stream()
                .anyMatch(x -> toId.equals(x.getEmployeeId()));
        if (alreadyOnTask) {
            assignmentRepo.delete(a);
        } else {
            a.setEmployeeId(toId);
            a.setAssignedBy(actorPk);
            assignmentRepo.save(a);
        }
        activityRepo.save(ActivityLog.builder()
                .entityType("TASK").entityId(t.getId()).action("REASSIGNED")
                .actorId(actorPk).actorName(actorName)
                .oldValue(fromName).newValue(toName)
                .message(trim(message, 500))
                .build());
    }

    private boolean isOpen(Task t) {
        return t.getStatus() != null && t.getStatus() != TaskStatus.DONE
                && !"CANCELLED".equals(t.getStatus().name());
    }

    private List<Pair> openAssignments(Long employeePk, Long projectId, Set<Long> taskIds) {
        List<Pair> out = new ArrayList<>();
        for (TaskAssignment a : assignmentRepo.findByEmployeeId(employeePk)) {
            Task t = taskRepo.findById(a.getTaskId()).orElse(null);
            if (t == null || !isOpen(t)) continue;
            if (projectId != null && !projectId.equals(t.getProjectId())) continue;
            if (taskIds != null && !taskIds.isEmpty() && !taskIds.contains(t.getId())) continue;
            out.add(new Pair(a, t));
        }
        return out;
    }

    private HandoverResponse.Row row(Task t, Map<Long, String> names) {
        return HandoverResponse.Row.builder()
                .taskId(t.getId()).projectId(t.getProjectId())
                .projectName(projectName(t.getProjectId(), names))
                .title(t.getTitle())
                .status(t.getStatus() == null ? null : t.getStatus().name())
                .dueDate(t.getDueDate()).build();
    }

    private String projectName(Long pid, Map<Long, String> cache) {
        if (pid == null) return "No project";
        return cache.computeIfAbsent(pid,
                k -> projectRepo.findById(k).map(Project::getName).orElse("Project #" + k));
    }

    private String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void sendAfterCommit(List<NotificationRequest> bells) {
        if (bells.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { notifier.sendAll(bells); }
            });
        } else {
            notifier.sendAll(bells);
        }
    }
}