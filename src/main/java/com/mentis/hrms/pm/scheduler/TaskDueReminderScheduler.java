package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.service.TaskEventNotifier;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Daily 8:30.
 *  - "due tomorrow": assignees only
 *  - overdue at day 1, 3, 7: assignees only (one bell per task, as before)
 *  - NEW Step 5: ONE digest per project per day ("3 overdue task(s) in X") to the
 *    project owner, MANAGER/LEAD members and team lead. They no longer get one bell
 *    per task.
 * Never touches PmAccessService.
 *
 * NOTE: the old version had @Transactional(readOnly = true). NotificationEmitter writes
 * a row, and writing inside a read-only transaction fails on MySQL. This version has
 * no outer transaction, so each bell is saved on its own.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDueReminderScheduler {

    private static final int[] OVERDUE_DAYS = {1, 3, 7};
    private static final int DIGEST_TITLES = 3;
    // project statuses that should not produce overdue digests (names checked as text)
    private static final Set<String> CLOSED = Set.of("COMPLETED", "CANCELLED", "ARCHIVED");

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter emitter;
    private final TaskEventNotifier notifier;

    @Scheduled(cron = "0 30 8 * * *")
    public void sendReminders() {
        runFor(LocalDate.now());
    }

    /** Public so a dev-only trigger or a test can call it with a chosen date. */
    public int runFor(LocalDate today) {
        int sent = 0;
        for (Task t : taskRepository.findOpenDueOn(today.plusDays(1), TaskStatus.DONE)) {
            sent += notify(t, assignees(t), "DUE_SOON",
                    "Due tomorrow: " + t.getTitle(), "This task is due tomorrow.", "NORMAL");
        }
        for (int d : OVERDUE_DAYS) {
            for (Task t : taskRepository.findOpenDueOn(today.minusDays(d), TaskStatus.DONE)) {
                sent += notify(t, assignees(t), "OVERDUE",
                        "Overdue: " + t.getTitle(),
                        "This task is " + d + " day(s) overdue.", "HIGH");
            }
        }
        sent += sendDigests(today);
        log.info("TaskDueReminderScheduler sent {} notification(s) for {}", sent, today);
        return sent;
    }

    // ── digest for owner / managers / leads ───────────────────────────────
    private int sendDigests(LocalDate today) {
        List<Project> projects = projectRepository.findAll().stream()
                .filter(p -> !isClosed(p))
                .collect(Collectors.toList());
        if (projects.isEmpty()) return 0;

        Map<Long, Project> byId = new LinkedHashMap<>();
        for (Project p : projects) byId.put(p.getId(), p);

        List<Task> overdue = taskRepository.findOverdueInProjects(byId.keySet(), today, TaskStatus.DONE);
        Map<Long, List<Task>> perProject = overdue.stream()
                .collect(Collectors.groupingBy(Task::getProjectId, LinkedHashMap::new, Collectors.toList()));

        int sent = 0;
        for (Map.Entry<Long, List<Task>> e : perProject.entrySet()) {
            Project p = byId.get(e.getKey());
            if (p == null) continue;
            List<Task> list = e.getValue();
            int n = list.size();

            String titles = list.stream().limit(DIGEST_TITLES).map(Task::getTitle)
                    .collect(Collectors.joining("; "));
            if (n > DIGEST_TITLES) titles += " and " + (n - DIGEST_TITLES) + " more";

            List<NotificationRequest> out = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Long pk : notifier.leadersOfProject(p.getId(), p.getTeamId())) {
                String code = notifier.codeOf(pk);
                if (code == null || !seen.add(code)) continue;
                out.add(notifier.build(code, "OVERDUE_DIGEST",
                        n + " overdue task(s) in " + p.getName(), titles,
                        n >= 5 ? "HIGH" : "NORMAL",
                        null, p.getId(), "/pm/06-tasks", null, "System"));
            }
            sent += notifier.sendAll(out);
        }
        return sent;
    }

    private boolean isClosed(Project p) {
        try {
            return p.getStatus() != null && CLOSED.contains(p.getStatus().name());
        } catch (Exception ex) {
            return false;
        }
    }

    // ── per-task bells to assignees (unchanged behaviour) ─────────────────
    private Set<Long> assignees(Task t) {
        Set<Long> ids = new LinkedHashSet<>();
        for (TaskAssignment a : taskAssignmentRepository.findByTaskId(t.getId()))
            ids.add(a.getEmployeeId());
        return ids;
    }

    private int notify(Task t, Set<Long> pks, String type, String title, String msg, String priority) {
        int n = 0;
        for (Long pk : pks) {
            try {
                String code = employeeRepository.findById(pk).map(Employee::getEmployeeId).orElse(null);
                if (code == null) continue;
                emitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type(type).action(type)
                        .title(title).message(msg)
                        .taskId(t.getId()).projectId(t.getProjectId())
                        .priority(priority)
                        .deepLink("/pm/03-my-work?task=" + t.getId())
                        .build());
                n++;
            } catch (Exception e) {
                log.warn("reminder failed for task {} emp {}: {}", t.getId(), pk, e.getMessage());
            }
        }
        return n;
    }
}