package com.mentis.hrms.pm.listener;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.event.EmployeeDeactivatedEvent;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import com.mentis.hrms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmployeeOffboardingListener {

    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final NotificationEmitter notificationEmitter;

    @EventListener
    @Transactional
    public void onEmployeeDeactivated(EmployeeDeactivatedEvent event) {
        if (event == null || event.getEmployeePk() == null) return;

        List<TaskAssignment> assignments = taskAssignmentRepository.findByEmployeeId(event.getEmployeePk());
        Set<Long> projectIds = new HashSet<>();

        for (TaskAssignment a : assignments) {
            Task task = taskRepository.findById(a.getTaskId()).orElse(null);
            if (task == null || task.getStatus() == TaskStatus.DONE) continue;
            if (task.getProjectId() != null) projectIds.add(task.getProjectId());
            taskAssignmentRepository.delete(a);
        }

        if (projectIds.isEmpty()) return;

        String name = event.getEmployeeName() == null ? ("Employee #" + event.getEmployeePk()) : event.getEmployeeName();
        String msg = "Employee " + name + " was offboarded. Their open tasks have been unassigned.";

        for (Long projectId : projectIds) {
            Project p = projectRepository.findById(projectId).orElse(null);
            if (p == null || p.getOwnerId() == null) continue;
            notifyManager(p, msg);
        }
    }

    private void notifyManager(Project p, String message) {
        Employee manager = employeeRepository.findById(p.getOwnerId()).orElse(null);
        if (manager == null || manager.getEmployeeId() == null || manager.getEmployeeId().isBlank()) return;
        try {
            notificationService.createNotification(
                    manager.getEmployeeId(), "EMPLOYEE",
                    "EMPLOYEE_OFFBOARDED",
                    "Team member offboarded",
                    message,
                    String.valueOf(p.getId()), "PROJECT",
                    "System");
        } catch (Exception ex) {
            log.warn("offboarding NotificationService failed for project {}: {}", p.getId(), ex.getMessage());
            try {
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(manager.getEmployeeId()).recipientType("EMPLOYEE")
                        .type("EMPLOYEE_OFFBOARDED").action("EMPLOYEE_OFFBOARDED")
                        .title("Team member offboarded")
                        .message(message)
                        .projectId(p.getId())
                        .priority("HIGH")
                        .deepLink("/pm/04-projects")
                        .build());
            } catch (Exception e2) {
                log.warn("offboarding notification emit failed for project {}: {}", p.getId(), e2.getMessage());
            }
        }
    }
}
