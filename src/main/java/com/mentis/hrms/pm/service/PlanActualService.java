package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.PlanActualResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanActualService {

    private final ProjectRepository projectRepo;
    private final TaskRepository taskRepo;
    private final TaskTimeLogRepository logRepo;
    private final ProjectBaselineHistoryRepository histRepo;
    private final PmAccessService access;

    @Transactional(readOnly = true)
    public PlanActualResponse get(Long projectId, Long userId) {
        access.requireCanViewProject(userId, projectId);
        if (!access.canViewFinance(userId, projectId))                 // ADAPT: your finance-read check
            throw new PmAccessDeniedException("You do not have access to Plan vs Actual for this project.");

        Project p = projectRepo.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        boolean baselined = p.getBaselinedAt() != null;

        List<Task> tasks = taskRepo.findByProjectId(projectId);        // ADAPT: your repo method
        Map<Long, Double> logged = new HashMap<>();
        if (!tasks.isEmpty()) {
            for (Object[] o : logRepo.sumMinutesByTask(tasks.stream().map(Task::getId).collect(Collectors.toList())))
                logged.put((Long) o[0], ((Number) o[1]).doubleValue() / 60.0);
        }

        List<PlanActualResponse.Row> rows = new ArrayList<>();
        int doneWithBaseline = 0, onTime = 0, late = 0, added = 0, slipN = 0;
        double slipSum = 0, baseHoursSum = 0, loggedHoursSum = 0;

        for (Task t : tasks) {
            LocalDate bd = t.getBaselineDue();
            Integer bhi = t.getBaselineEstimateHours();
            Double bh = bhi == null ? null : bhi.doubleValue();                        // ADAPT field (Double or BigDecimal -> convert)
            LocalDate done = (t.getStatus() == TaskStatus.DONE && t.getCompletedAt() != null)
                    ? t.getCompletedAt().toLocalDate() : null;         // ADAPT: LocalDateTime or LocalDate
            Integer slip = bd == null ? null : (int) ChronoUnit.DAYS.between(bd, done != null ? done : today);
            double lh = logged.getOrDefault(t.getId(), 0.0);
            boolean addedAfter = baselined && t.getCreatedAt() != null
                    && t.getCreatedAt().isAfter(p.getBaselinedAt());   // ADAPT types

            if (bd != null && done != null) { doneWithBaseline++; if (!done.isAfter(bd)) onTime++; }
            if (slip != null && (done != null || today.isAfter(bd))) { slipSum += Math.max(0, slip); slipN++; }
            if (slip != null && slip > 0) late++;
            if (addedAfter) added++;
            if (bh != null && bh > 0) { baseHoursSum += bh; loggedHoursSum += lh; }

            rows.add(PlanActualResponse.Row.builder()
                    .taskId(t.getId()).title(t.getTitle()).status(t.getStatus().name())
                    .baselineDue(bd).currentDue(t.getDueDate()).completedOn(done)
                    .slipDays(slip).baselineHours(bh).loggedHours(Math.round(lh * 10) / 10.0)
                    .addedAfterBaseline(addedAfter).build());
        }

        PlanActualResponse.Summary s = PlanActualResponse.Summary.builder()
                .onTimePct(doneWithBaseline == 0 ? null : (int) Math.round(onTime * 100.0 / doneWithBaseline))
                .avgSlipDays(slipN == 0 ? null : Math.round(slipSum / slipN * 10) / 10.0)
                .hoursVariancePct(baseHoursSum <= 0 ? null
                        : Math.round((loggedHoursSum - baseHoursSum) / baseHoursSum * 1000) / 10.0)
                .lateTasks(late).addedAfterBaseline(added).build();

        boolean locked = access.isProjectLocked(projectId);
        return PlanActualResponse.builder()
                .projectId(projectId).baselined(baselined).baselinedAt(p.getBaselinedAt())
                .baselineVersion((int) histRepo.countByProjectId(projectId) + (baselined ? 1 : 0))   // ADAPT
                .locked(locked)
                .canManage(!locked && access.canAdministerProject(userId, projectId))
                .summary(s).rows(rows).build();
    }
}