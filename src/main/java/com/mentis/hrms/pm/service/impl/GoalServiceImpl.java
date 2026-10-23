package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreateGoalRequest;
import com.mentis.hrms.pm.dto.request.CreateKeyResultRequest;
import com.mentis.hrms.pm.dto.response.GoalResponse;
import com.mentis.hrms.pm.dto.response.KeyResultResponse;
import com.mentis.hrms.pm.entity.Goal;
import com.mentis.hrms.pm.entity.KeyResult;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.enums.GoalStatus;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.GoalRepository;
import com.mentis.hrms.pm.repository.KeyResultRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.service.GoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final KeyResultRepository keyResultRepository;
    private final ProjectRepository projectRepository;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;

    @Override
    @Transactional
    public GoalResponse create(CreateGoalRequest req, Long userId) {
        Goal g = Goal.builder()
                .projectId(req.getProjectId()).ownerId(req.getOwnerId())
                .title(req.getTitle()).description(req.getDescription())
                .quarter(req.getQuarter()).year(req.getYear())
                .status(GoalStatus.ACTIVE).progressPct(0)
                .createdBy(userId).build();
        Goal saved = goalRepository.save(g);
        publisher.publish("POST", "/api/pm/goals/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    @Override @Transactional(readOnly = true)
    public GoalResponse get(Long id) {
        return toResponse(goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + id)));
    }

    @Override @Transactional(readOnly = true)
    public List<GoalResponse> listByProject(Long projectId) {
        return goalRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override @Transactional(readOnly = true)
    public List<GoalResponse> listByQuarter(String quarter, Integer year) {
        return goalRepository.findByQuarterAndYear(quarter, year)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public GoalResponse update(Long id, CreateGoalRequest req, Long userId) {
        Goal g = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + id));
        if (req.getTitle() != null) g.setTitle(req.getTitle());
        if (req.getDescription() != null) g.setDescription(req.getDescription());
        if (req.getOwnerId() != null) g.setOwnerId(req.getOwnerId());
        if (req.getProjectId() != null) g.setProjectId(req.getProjectId());
        if (req.getQuarter() != null) g.setQuarter(req.getQuarter());
        if (req.getYear() != null) g.setYear(req.getYear());
        Goal saved = goalRepository.save(g);
        publisher.publish("PATCH", "/api/pm/goals/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Goal g = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + id));
        keyResultRepository.deleteByGoalId(id);
        goalRepository.delete(g);
        publisher.publish("DELETE", "/api/pm/goals/" + id, String.valueOf(userId));
    }

    @Override
    @Transactional
    public KeyResultResponse addKeyResult(Long goalId, CreateKeyResultRequest req, Long userId) {
        Goal g = goalRepository.findById(goalId)
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + goalId));
        KeyResult kr = KeyResult.builder()
                .goalId(goalId).metric(req.getMetric()).unit(req.getUnit())
                .targetValue(req.getTargetValue()).currentValue(req.getCurrentValue())
                .baselineValue(req.getBaselineValue())
                .progressPct(computePct(req.getBaselineValue(), req.getTargetValue(), req.getCurrentValue()))
                .build();
        KeyResult saved = keyResultRepository.save(kr);
        recomputeGoalProgress(goalId);
        publisher.publish("POST", "/api/pm/goals/" + goalId + "/key-results", String.valueOf(userId));
        return toResponseKr(saved);
    }

    @Override
    @Transactional
    public KeyResultResponse updateKeyResult(Long krId, CreateKeyResultRequest req, Long userId) {
        KeyResult kr = keyResultRepository.findById(krId)
                .orElseThrow(() -> new ResourceNotFoundException("KR not found: " + krId));
        if (req.getMetric() != null) kr.setMetric(req.getMetric());
        if (req.getUnit() != null) kr.setUnit(req.getUnit());
        if (req.getTargetValue() != null) kr.setTargetValue(req.getTargetValue());
        if (req.getCurrentValue() != null) kr.setCurrentValue(req.getCurrentValue());
        if (req.getBaselineValue() != null) kr.setBaselineValue(req.getBaselineValue());
        kr.setProgressPct(computePct(kr.getBaselineValue(), kr.getTargetValue(), kr.getCurrentValue()));
        KeyResult saved = keyResultRepository.save(kr);
        recomputeGoalProgress(kr.getGoalId());
        publisher.publish("PATCH", "/api/pm/key-results/" + krId, String.valueOf(userId));
        return toResponseKr(saved);
    }

    @Override
    @Transactional
    public void deleteKeyResult(Long krId, Long userId) {
        KeyResult kr = keyResultRepository.findById(krId)
                .orElseThrow(() -> new ResourceNotFoundException("KR not found: " + krId));
        Long goalId = kr.getGoalId();
        keyResultRepository.delete(kr);
        recomputeGoalProgress(goalId);
        publisher.publish("DELETE", "/api/pm/key-results/" + krId, String.valueOf(userId));
    }

    // ── helpers ──

    private void recomputeGoalProgress(Long goalId) {
        List<KeyResult> krs = keyResultRepository.findByGoalIdOrderByCreatedAtAsc(goalId);
        int avg = krs.isEmpty() ? 0
                : (int) Math.round(krs.stream().mapToInt(KeyResult::getProgressPct).average().orElse(0));
        goalRepository.findById(goalId).ifPresent(g -> {
            g.setProgressPct(avg);
            // auto status
            if (avg >= 100) g.setStatus(GoalStatus.COMPLETED);
            else if (avg >= 70) g.setStatus(GoalStatus.ON_TRACK);
            else if (avg >= 40) g.setStatus(GoalStatus.AT_RISK);
            else g.setStatus(GoalStatus.OFF_TRACK);
            goalRepository.save(g);
        });
    }

    private int computePct(Double baseline, Double target, Double current) {
        if (target == null || current == null) return 0;
        double base = baseline != null ? baseline : 0.0;
        if (target.equals(base)) return current >= target ? 100 : 0;
        double pct = ((current - base) / (target - base)) * 100;
        return (int) Math.max(0, Math.min(100, Math.round(pct)));
    }

    private GoalResponse toResponse(Goal g) {
        List<KeyResultResponse> krs = keyResultRepository.findByGoalIdOrderByCreatedAtAsc(g.getId())
                .stream().map(this::toResponseKr).collect(Collectors.toList());
        String projectName = g.getProjectId() != null
                ? projectRepository.findById(g.getProjectId()).map(Project::getName).orElse(null)
                : null;
        String ownerName = g.getOwnerId() != null
                ? employeeRepository.findById(g.getOwnerId())
                .map(e -> ((safe(e.getFirstName()) + " " + safe(e.getLastName())).trim()))
                .orElse(null) : null;
        return GoalResponse.builder()
                .id(g.getId()).projectId(g.getProjectId()).projectName(projectName)
                .ownerId(g.getOwnerId()).ownerName(ownerName)
                .title(g.getTitle()).description(g.getDescription())
                .quarter(g.getQuarter()).year(g.getYear())
                .status(g.getStatus()).progressPct(g.getProgressPct())
                .keyResults(krs)
                .createdAt(g.getCreatedAt()).updatedAt(g.getUpdatedAt())
                .build();
    }

    private KeyResultResponse toResponseKr(KeyResult kr) {
        return KeyResultResponse.builder()
                .id(kr.getId()).goalId(kr.getGoalId())
                .metric(kr.getMetric()).unit(kr.getUnit())
                .targetValue(kr.getTargetValue()).currentValue(kr.getCurrentValue())
                .baselineValue(kr.getBaselineValue()).progressPct(kr.getProgressPct())
                .createdAt(kr.getCreatedAt()).updatedAt(kr.getUpdatedAt()).build();
    }

    private String safe(String s) { return s == null ? "" : s; }
}