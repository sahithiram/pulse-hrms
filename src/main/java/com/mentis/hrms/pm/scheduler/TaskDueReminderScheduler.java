package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** Daily 8:30: "due tomorrow" + overdue at day 1, 3, 7. Never touches PmAccessService. */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDueReminderScheduler {

    private static final int[] OVERDUE_DAYS = {1, 3, 7};

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter emitter;

    @Scheduled(cron = "0 30 8 * * *")
    @Transactional(readOnly = true)
    public void sendReminders() {
        runFor(LocalDate.now());
    }

    /** Public so a dev-only trigger or a test can call it with a chosen date. */
    @Transactional(readOnly = true)
    public int runFor(LocalDate today) {
        int sent = 0;
        for (Task t : taskRepository.findOpenDueOn(today.plusDays(1), TaskStatus.DONE)) {
            sent += notify(t, audience(t, false), "DUE_SOON",
                    "Due tomorrow: " + t.getTitle(), "This task is due tomorrow.", "NORMAL");
        }
        for (int d : OVERDUE_DAYS) {
            for (Task t : taskRepository.findOpenDueOn(today.minusDays(d), TaskStatus.DONE)) {
                sent += notify(t, audience(t, true), "OVERDUE",
                        "Overdue: " + t.getTitle(),
                        "This task is " + d + " day(s) overdue.", "HIGH");
            }
        }
        log.info("TaskDueReminderScheduler sent {} notification(s) for {}", sent, today);
        return sent;
    }

    private Set<Long> audience(Task t, boolean includeOwner) {
        Set<Long> ids = new LinkedHashSet<>();
        for (TaskAssignment a : taskAssignmentRepository.findByTaskId(t.getId()))
            ids.add(a.getEmployeeId());
        if (includeOwner && t.getProjectId() != null)
            projectRepository.findById(t.getProjectId())
                    .ifPresent(p -> { if (p.getOwnerId() != null) ids.add(p.getOwnerId()); });
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