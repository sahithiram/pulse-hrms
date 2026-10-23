package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.entity.ApprovalRequest;
import com.mentis.hrms.pm.repository.ApprovalRequestRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Phase 7 — escalate PENDING approvals older than 3 days.
 * Runs daily at 8:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApprovalSlaScheduler {

    private static final int SLA_DAYS = 3;

    private final ApprovalRequestRepository repo;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter emitter;

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void escalateStaleApprovals() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(SLA_DAYS);
        List<ApprovalRequest> stale = repo.findByStatusAndCreatedAtBefore("PENDING", cutoff);
        if (stale.isEmpty()) return;

        for (ApprovalRequest ar : stale) {
            try {
                String code = employeeRepository.findById(ar.getApproverId())
                        .map(Employee::getEmployeeId).orElse(null);
                if (code == null) continue;
                emitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type("APPROVAL_OVERDUE").action("APPROVAL_OVERDUE")
                        .title("⏰ Approval overdue: " + ar.getEntityTitle())
                        .message("Pending for more than " + SLA_DAYS + " days")
                        .priority("HIGH")
                        .deepLink("/pm/02-inbox?tab=approvals&focus=" + ar.getId())
                        .build());
            } catch (Exception e) {
                log.warn("approval escalation failed for {}: {}", ar.getId(), e.getMessage());
            }
        }
        log.info("ApprovalSlaScheduler escalated {} stale approvals", stale.size());
    }
}