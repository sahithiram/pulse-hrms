package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.CreateRiskRequest;
import com.mentis.hrms.pm.dto.response.RiskResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Risk;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.RiskRepository;
import com.mentis.hrms.pm.service.RiskService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskServiceImpl implements RiskService {

    private final RiskRepository repo;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;
    private final NotificationEmitter notificationEmitter;
    private final ActivityLogRepository activityLogRepository;

    @Override
    @Transactional
    public RiskResponse create(CreateRiskRequest req, Long userId) {
        if (!projectRepository.existsById(req.getProjectId()))
            throw new ResourceNotFoundException("Project not found: " + req.getProjectId());

        String likelihood = req.getLikelihood() != null ? req.getLikelihood() : "MEDIUM";
        String impact = req.getImpact() != null ? req.getImpact() : "MEDIUM";
        int score = Risk.computeScore(likelihood, impact);

        Risk r = Risk.builder()
                .projectId(req.getProjectId())
                .title(req.getTitle())
                .description(req.getDescription())
                .likelihood(likelihood).impact(impact).score(score)
                .status("OPEN")
                .ownerId(req.getOwnerId())
                .mitigation(req.getMitigation())
                .reviewDate(req.getReviewDate())
                .createdBy(userId)
                .build();
        Risk saved = repo.save(r);

        // Notify owner + project owner if HIGH/CRITICAL
        if (score >= 15) {
            if (saved.getOwnerId() != null && !saved.getOwnerId().equals(userId)) {
                notifyEmployee(saved.getOwnerId(), "RISK_HIGH",
                        "High risk logged: " + saved.getTitle(),
                        "Score " + score + " — needs mitigation plan",
                        saved.getProjectId(), null, userId, "HIGH",
                        "/pm/09-quality?tab=risks&id=" + saved.getId());
            }
            projectRepository.findById(saved.getProjectId()).ifPresent(p -> {
                if (p.getOwnerId() != null
                        && !p.getOwnerId().equals(userId)
                        && !p.getOwnerId().equals(saved.getOwnerId())) {
                    notifyEmployee(p.getOwnerId(), "RISK_HIGH",
                            "High risk logged: " + saved.getTitle(),
                            "Score " + score,
                            saved.getProjectId(), null, userId, "HIGH",
                            "/pm/09-quality?tab=risks&id=" + saved.getId());
                }
            });
        }

        logRisk(saved.getId(), "CREATED", userId, null, saved.getStatus(), "Risk created: " + saved.getTitle());
        publisher.publish("POST", "/api/pm/risks/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public RiskResponse get(Long id) {
        return toResponse(repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskResponse> listByProject(Long projectId) {
        return repo.findByProjectIdOrderByScoreDesc(projectId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RiskResponse updateStatus(Long id, String status, Long userId) {
        Risk r = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk not found: " + id));
        String old = r.getStatus();
        r.setStatus(status);
        Risk saved = repo.save(r);
        logRisk(saved.getId(), "STATUS_CHANGED", userId, old, saved.getStatus(),
                "Risk status: " + old + " → " + saved.getStatus());
        publisher.publish("PATCH", "/api/pm/risks/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public RiskResponse updateMitigation(Long id, String mitigation, Long userId) {
        Risk r = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk not found: " + id));
        String old = r.getMitigation();
        r.setMitigation(mitigation);
        Risk saved = repo.save(r);
        logRisk(saved.getId(), "UPDATED", userId, old, saved.getMitigation(), "Mitigation plan updated");
        publisher.publish("PATCH", "/api/pm/risks/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Risk r = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk not found: " + id));
        logRisk(r.getId(), "DELETED", userId, r.getTitle(), null, "Risk deleted: " + r.getTitle());
        repo.delete(r);
        publisher.publish("DELETE", "/api/pm/risks/" + id, String.valueOf(userId));
    }

    private void logRisk(Long riskId, String action, Long actorId,
                         String oldV, String newV, String msg) {
        if (riskId == null) return;
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("RISK").entityId(riskId).action(action)
                    .actorId(actorId).actorName(nameOf(actorId))
                    .oldValue(oldV).newValue(newV).message(msg).build());
        } catch (Exception e) {
            log.warn("risk activity log failed: {}", e.getMessage());
        }
    }

    // ── helpers ──

    private RiskResponse toResponse(Risk r) {
        String projectName = r.getProjectId() != null
                ? projectRepository.findById(r.getProjectId()).map(Project::getName).orElse(null)
                : null;
        String severity = r.getScore() >= 15 ? "HIGH" : r.getScore() >= 9 ? "MEDIUM" : "LOW";
        return RiskResponse.builder()
                .id(r.getId())
                .projectId(r.getProjectId()).projectName(projectName)
                .title(r.getTitle()).description(r.getDescription())
                .likelihood(r.getLikelihood()).impact(r.getImpact())
                .score(r.getScore()).severity(severity)
                .status(r.getStatus())
                .ownerId(r.getOwnerId()).ownerName(nameOf(r.getOwnerId()))
                .mitigation(r.getMitigation())
                .reviewDate(r.getReviewDate())
                .createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt())
                .build();
    }

    private String nameOf(Long pk) {
        if (pk == null) return null;
        return employeeRepository.findById(pk)
                .map(e -> (safe(e.getFirstName()) + " " + safe(e.getLastName())).trim())
                .orElse("Employee #" + pk);
    }

    private String safe(String s) { return s == null ? "" : s; }

    private void notifyEmployee(Long empPk, String type, String title, String message,
                                Long projectId, Long taskId, Long actorPk, String priority,
                                String deepLink) {
        try {
            String code = employeeRepository.findById(empPk).map(Employee::getEmployeeId).orElse(null);
            if (code == null) return;
            notificationEmitter.emit(NotificationRequest.builder()
                    .recipientId(code).recipientType("EMPLOYEE")
                    .type(type).action(type)
                    .title(title).message(message)
                    .projectId(projectId).taskId(taskId)
                    .actorId(actorPk).actorName(nameOf(actorPk))
                    .priority(priority).deepLink(deepLink)
                    .build());
        } catch (Exception e) {
            log.warn("risk notify failed: {}", e.getMessage());
        }
    }
}