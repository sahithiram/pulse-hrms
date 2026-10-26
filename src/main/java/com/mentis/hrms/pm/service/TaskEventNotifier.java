package com.mentis.hrms.pm.service;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.Team;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 5 — who gets a bell when a task changes status, and the helpers the
 * schedulers reuse.
 *
 * Rules:
 *  - the person who made the change is never notified
 *  - one bell per person per change (first matching role wins)
 *  - nothing is sent unless the status change was committed
 *  - Viewers and HR are never picked here (only owner, MANAGER/LEAD members,
 *    team leads and assignees are)
 *  - NEVER calls PmAccessService (schedulers use this class and have no web user)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskEventNotifier {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamRepository teamRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter emitter;
    private final PlatformTransactionManager txManager;

    // ══════════════════════════════════════════════════════════
    // PLAN  (built inside the caller's transaction, sent after commit)
    // ══════════════════════════════════════════════════════════

    public class Plan {
        private final List<NotificationRequest> requests = new ArrayList<>();
        private final Set<Long> covered = new HashSet<>();

        /** Employee PKs that already get a Step 5 bell for this change. */
        public Set<Long> covered() { return covered; }

        public int size() { return requests.size(); }

        /** Sends only after the surrounding transaction commits. */
        public void sendAfterCommit() {
            if (requests.isEmpty()) return;
            final List<NotificationRequest> toSend = new ArrayList<>(requests);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { sendAll(toSend); }
                });
            } else {
                sendAll(toSend);
            }
        }
    }

    /** Never throws. On any failure it returns an empty plan. */
    public Plan plan(Task task, TaskStatus from, TaskStatus to, Long actorId, String reason) {
        Plan plan = new Plan();
        try {
            if (task == null || to == null || to == from) return plan;
            Project p = task.getProjectId() == null ? null
                    : projectRepository.findById(task.getProjectId()).orElse(null);
            String actorName = nameOf(actorId);
            String title = task.getTitle();
            String proj = p != null && p.getName() != null ? " in " + p.getName() : "";
            String why = reason == null || reason.isBlank() ? "" : ": " + trim(reason, 140);
            Set<Long> assignees = assigneesOf(task.getId());

            if (to == TaskStatus.BLOCKED) {
                add(plan, leaders(task.getProjectId(), task.getTeamId()), actorId, task,
                        "TASK_BLOCKED", "Task blocked: " + title,
                        actorName + " blocked this task" + proj + why, "HIGH");

            } else if (to == TaskStatus.IN_REVIEW) {
                add(plan, leaders(task.getProjectId(), task.getTeamId()), actorId, task,
                        "TASK_IN_REVIEW", "Ready for review: " + title,
                        actorName + " sent this task for review" + proj, "NORMAL");

            } else if (to == TaskStatus.DONE) {
                add(plan, ownerAndManagers(task.getProjectId()), actorId, task,
                        "TASK_DONE", "Task done: " + title,
                        actorName + " marked this task done" + proj, "NORMAL");
                // assignees only when someone ELSE (e.g. the TL after review) marked it done
                if (actorId == null || !assignees.contains(actorId)) {
                    add(plan, assignees, actorId, task,
                            "TASK_DONE", "Your task is done: " + title,
                            actorName + " marked it done" + proj, "NORMAL");
                }

            } else if (from == TaskStatus.IN_REVIEW
                    && (to == TaskStatus.IN_PROGRESS || to == TaskStatus.TODO)
                    && (actorId == null || !assignees.contains(actorId))) {
                // sent back by a reviewer (an assignee withdrawing their own task notifies nobody)
                add(plan, assignees, actorId, task,
                        "TASK_SENT_BACK", "Sent back for changes: " + title,
                        actorName + " sent this back" + proj + why, "HIGH");
            }
        } catch (Exception e) {
            log.warn("TaskEventNotifier.plan failed for task {}: {}",
                    task != null ? task.getId() : null, e.getMessage());
        }
        return plan;
    }

    private void add(Plan plan, Set<Long> recipients, Long actorId, Task task,
                     String type, String title, String message, String priority) {
        for (Long pk : recipients) {
            if (pk == null || pk.equals(actorId) || plan.covered.contains(pk)) continue;
            String code = codeOf(pk);
            if (code == null) continue;
            plan.requests.add(build(code, type, title, message, priority,
                    task.getId(), task.getProjectId(),
                    "/pm/06-tasks?view=board&open=" + task.getId(),
                    actorId, nameOf(actorId)));
            plan.covered.add(pk);
        }
    }

    // ══════════════════════════════════════════════════════════
    // RECIPIENT LOOKUP  (no PmAccessService, safe from schedulers)
    // ══════════════════════════════════════════════════════════

    /** Project owner + MANAGER/LEAD members + team lead(s). The "who must know" group. */
    public Set<Long> leaders(Long projectId, Long taskTeamId) {
        Set<Long> out = new LinkedHashSet<>();
        Project p = projectId == null ? null : projectRepository.findById(projectId).orElse(null);
        if (p != null && p.getOwnerId() != null) out.add(p.getOwnerId());
        if (projectId != null) {
            for (ProjectMember m : projectMemberRepository.findByProjectId(projectId)) {
                String r = m.getRole() == null ? "" : m.getRole().name();
                if (r.equals("MANAGER") || r.equals("LEAD")) out.add(m.getEmployeeId());
            }
        }
        addTeamLead(out, p != null ? p.getTeamId() : null);
        addTeamLead(out, taskTeamId);
        return out;
    }

    public Set<Long> leadersOfProject(Long projectId, Long projectTeamId) {
        return leaders(projectId, projectTeamId);
    }

    /** Project owner + MANAGER members (no leads). Used for "Done" and escalation level 2. */
    public Set<Long> ownerAndManagers(Long projectId) {
        Set<Long> out = new LinkedHashSet<>();
        if (projectId == null) return out;
        projectRepository.findById(projectId).ifPresent(p -> {
            if (p.getOwnerId() != null) out.add(p.getOwnerId());
        });
        for (ProjectMember m : projectMemberRepository.findByProjectId(projectId)) {
            if (m.getRole() != null && m.getRole().name().equals("MANAGER")) out.add(m.getEmployeeId());
        }
        return out;
    }

    private void addTeamLead(Set<Long> out, Long teamId) {
        if (teamId == null) return;
        teamRepository.findById(teamId).map(Team::getLeadEmployeeId).ifPresent(out::add);
    }

    private Set<Long> assigneesOf(Long taskId) {
        Set<Long> s = new LinkedHashSet<>();
        for (TaskAssignment a : taskAssignmentRepository.findByTaskId(taskId)) s.add(a.getEmployeeId());
        return s;
    }

    // ══════════════════════════════════════════════════════════
    // HELPERS SHARED WITH THE SCHEDULERS
    // ══════════════════════════════════════════════════════════

    public String codeOf(Long employeePk) {
        if (employeePk == null) return null;
        try {
            return employeeRepository.findById(employeePk).map(e -> e.getEmployeeId()).orElse(null);
        } catch (Exception ex) {
            log.warn("codeOf failed for pk={}: {}", employeePk, ex.getMessage());
            return null;
        }
    }

    public String nameOf(Long pk) {
        if (pk == null) return "Someone";
        try {
            return employeeRepository.findById(pk).map(e -> {
                String first = e.getFirstName() == null ? "" : e.getFirstName();
                String last = e.getLastName() == null ? "" : e.getLastName();
                String full = (first + " " + last).trim();
                if (!full.isEmpty()) return full;
                if (e.getEmail() != null) return e.getEmail();
                return "Employee #" + pk;
            }).orElse("Employee #" + pk);
        } catch (Exception ex) {
            return "Employee #" + pk;
        }
    }

    public NotificationRequest build(String recipientCode, String type, String title, String message,
                                     String priority, Long taskId, Long projectId, String deepLink,
                                     Long actorId, String actorName) {
        return NotificationRequest.builder()
                .recipientId(recipientCode).recipientType("EMPLOYEE")
                .type(type).action(type)
                .title(title).message(message)
                .taskId(taskId).projectId(projectId)
                .actorId(actorId).actorName(actorName)
                .priority(priority)
                .deepLink(deepLink)
                .build();
    }

    /**
     * Each bell in its OWN new transaction. Needed because this runs in afterCommit
     * (the old transaction can no longer write) and because one failure must not
     * stop the others.
     */
    public int sendAll(List<NotificationRequest> requests) {
        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        int sent = 0;
        for (NotificationRequest r : requests) {
            try {
                tt.executeWithoutResult(status -> emitter.emit(r));
                sent++;
            } catch (Exception e) {
                log.warn("notification {} to {} failed: {}", r.getType(), r.getRecipientId(), e.getMessage());
            }
        }
        return sent;
    }

    private String trim(String s, int max) {
        String c = s.replaceAll("\\s+", " ").trim();
        return c.length() <= max ? c : c.substring(0, max) + "…";
    }
}