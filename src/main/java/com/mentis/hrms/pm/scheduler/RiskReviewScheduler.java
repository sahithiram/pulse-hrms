package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.entity.Risk;
import com.mentis.hrms.pm.repository.RiskRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Phase 7 — weekly reminder for open HIGH risks (score >= 15).
 * Runs every Monday 9:00 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RiskReviewScheduler {

    private final RiskRepository repo;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter emitter;

    @Scheduled(cron = "0 0 9 * * MON")
    @Transactional
    public void weeklyRiskReminder() {
        List<Risk> high = repo.findByScoreGreaterThanEqualAndStatusNotOrderByScoreDesc(15, "RESOLVED");
        if (high.isEmpty()) return;

        for (Risk r : high) {
            if (r.getOwnerId() == null) continue;
            try {
                String code = employeeRepository.findById(r.getOwnerId())
                        .map(Employee::getEmployeeId).orElse(null);
                if (code == null) continue;
                emitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type("RISK_WEEKLY_REVIEW").action("RISK_WEEKLY_REVIEW")
                        .title("🔴 Weekly risk review: " + r.getTitle())
                        .message("Score " + r.getScore() + " — please review mitigation plan")
                        .projectId(r.getProjectId())
                        .priority("HIGH")
                        .deepLink("/pm/09-quality?tab=risks&id=" + r.getId())
                        .build());
            } catch (Exception e) {
                log.warn("risk reminder failed for {}: {}", r.getId(), e.getMessage());
            }
        }
        log.info("RiskReviewScheduler notified owners of {} high risks", high.size());
    }
}