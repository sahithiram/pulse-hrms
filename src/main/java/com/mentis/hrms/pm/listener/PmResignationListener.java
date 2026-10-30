package com.mentis.hrms.pm.listener;

import com.mentis.hrms.event.EmployeeResignedEvent;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.service.PmHandoverFinder;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.EmailService;
import com.mentis.hrms.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PmResignationListener {

    private final PmHandoverFinder finder;
    private final ProjectRepository projectRepo;
    private final EmployeeRepository employeeRepo;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ActivityLogRepository activityRepo;

    @Value("${app.base-url:}") private String baseUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(EmployeeResignedEvent e) {
        try {
            PmHandoverFinder.Detail d = finder.detail(e.employeeId());
            if (d == null || (d.tasks().isEmpty() && d.ownedProjects().isEmpty() && d.ledTeams().isEmpty())) {
                log.info("Resignation of employee {}: nothing to hand over", e.employeeId());
                return;
            }

            // Who must act: owners of projects the leaver has tasks in, plus HR users.
            Set<Long> recipients = new LinkedHashSet<>();
            for (var t : d.tasks()) {
                projectRepo.findById(t.projectId())
                        .ifPresent(p -> addIfNotNull(recipients, p.getOwnerId()));
            }
            recipients.addAll(hrEmployeeIds());
            recipients.remove(e.employeeId());

            String link = (baseUrl == null || baseUrl.isBlank() ? "" : baseUrl)
                    + "/pm/handover.html?from=" + e.employeeId();
            String title = d.name() + " is leaving: handover needed";
            String msg = d.tasks().size() + " open task(s), " + d.ownedProjects().size()
                    + " project(s) owned, " + d.ledTeams().size() + " team(s) led"
                    + (d.lastWorkingDay() == null ? "" : ". Last working day: " + d.lastWorkingDay()) + ".";

            for (Long to : recipients) {
                try {
                    notificationService.createNotification(
                            String.valueOf(to),
                            "EMPLOYEE",
                            "HANDOVER_REQUIRED",
                            title,
                            msg,
                            String.valueOf(e.employeeId()),
                            "EMPLOYEE",
                            "PM_SYSTEM");
                } catch (Exception ex) {
                    log.warn("Resignation bell failed for {}: {}", to, ex.getMessage());
                }
                try {
                    String mail = employeeRepo.findById(to)
                            .map(Employee::getEmail).orElse(null);
                    if (mail != null && !mail.isBlank()) {
                        emailService.send(mail, title, msg + "\n\nOpen: " + link);
                    }
                } catch (Exception ex) {
                    log.warn("Resignation email failed for {}: {}", to, ex.getMessage());
                }
            }

            activityRepo.save(ActivityLog.builder()
                    .entityType("EMPLOYEE")
                    .entityId(e.employeeId())
                    .action("RESIGNATION_HANDOVER_ALERT")
                    .actorId(e.actorId())
                    .newValue(msg)
                    .message(title + " — " + msg)
                    .build());
        } catch (Exception ex) {
            log.warn("PM resignation handler failed for employee {}: {}", e.employeeId(), ex.getMessage(), ex);
        }
    }

    private List<Long> hrEmployeeIds() {
        return employeeRepo.findAll().stream()
                .filter(emp -> emp.getRole() == UserRole.HR)
                .map(Employee::getId)
                .collect(Collectors.toList());
    }

    private static void addIfNotNull(Set<Long> s, Long v) { if (v != null) s.add(v); }
}