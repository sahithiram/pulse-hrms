package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.VarianceReport;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import com.mentis.hrms.pm.security.PmCurrentUserHolder;
import com.mentis.hrms.pm.security.PmUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.repository.ProjectRepository;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectVarianceService {

    private final TaskRepository taskRepository;
    private final TaskTimeLogRepository timeLogRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ProjectRepository projectRepository;

    /** Copies each task's current dates and estimate into its baseline columns. */
    @Transactional
    public int captureBaseline(Long projectId, boolean overwrite, Long actorPk) {
        int n = 0;
        for (Task t : taskRepository.findByProjectId(projectId)) {
            boolean has = t.getBaselineStart() != null || t.getBaselineDue() != null
                    || t.getBaselineEstimateHours() != null;
            if (has && !overwrite) continue;
            t.setBaselineStart(t.getStartDate());
            t.setBaselineDue(t.getDueDate());
            t.setBaselineEstimateHours(t.getEstimateHours());
            taskRepository.save(t);
            n++;
        }
        return n;   // no activity row here; ProjectServiceImpl logs once
    }

    @Transactional(readOnly = true)
    public VarianceReport report(Long projectId) {
        LocalDateTime baselinedAt = projectRepository.findById(projectId)
                .map(Project::getBaselinedAt).orElse(null);
        LocalDate today = LocalDate.now();
        List<VarianceReport.Row> rows = new ArrayList<>();
        double base = 0, plan = 0, act = 0;
        int noBase = 0, late = 0;

        for (Task t : taskRepository.findByProjectId(projectId)) {
            boolean hasBase = t.getBaselineStart() != null || t.getBaselineDue() != null
                    || t.getBaselineEstimateHours() != null;
            double planned = t.getEstimateHours() == null ? 0 : t.getEstimateHours();
            Double baseH = t.getBaselineEstimateHours() == null ? null : t.getBaselineEstimateHours().doubleValue();
            double actual = (t.getLoggedMinutes() == null ? 0 : t.getLoggedMinutes()) / 60.0;

            boolean done = t.getStatus() == TaskStatus.DONE;
            LocalDate completed = t.getCompletedAt() == null ? null : t.getCompletedAt().toLocalDate();
            LocalDate forecast = done
                    ? (completed != null ? completed : t.getDueDate())
                    : (t.getDueDate() != null && t.getDueDate().isBefore(today) ? today : t.getDueDate());
            Integer slip = (t.getBaselineDue() != null && forecast != null)
                    ? (int) ChronoUnit.DAYS.between(t.getBaselineDue(), forecast) : null;
            if (slip != null && slip > 0) late++;
            if (!hasBase) noBase++;

            // "Added after baseline" = task was created after the project was baselined.
            // hasBase (no baseline columns captured) is a separate concept and still
            // drives the tasksWithoutBaseline summary counter.
            boolean added = baselinedAt != null && t.getCreatedAt() != null
                    && t.getCreatedAt().isAfter(baselinedAt);

            if (baseH != null) base += baseH;
            plan += planned;
            act += actual;

            rows.add(VarianceReport.Row.builder()
                    .taskId(t.getId()).title(t.getTitle())
                    .status(t.getStatus() == null ? null : t.getStatus().name())
                    .baselineHours(baseH).plannedHours(planned).actualHours(round(actual))
                    .hoursVariance(round(actual - (baseH != null ? baseH : planned)))
                    .baselineStart(t.getBaselineStart()).baselineDue(t.getBaselineDue())
                    .dueDate(t.getDueDate()).completedDate(completed)
                    .slipDays(slip).addedAfterBaseline(added)
                    .build());
        }

        return VarianceReport.builder()
                .projectId(projectId)
                .baselineHours(round(base)).plannedHours(round(plan)).actualHours(round(act))
                .hoursVariance(round(act - (base > 0 ? base : plan)))
                .billableHours(round(timeLogRepository.sumMinutesForProjectByBillable(projectId, true) / 60.0))
                .nonBillableHours(round(timeLogRepository.sumMinutesForProjectByBillable(projectId, false) / 60.0))
                .overtimeHours(round(timeLogRepository.sumOvertimeMinutesForProject(projectId) / 60.0))
                .tasksWithoutBaseline(noBase).lateTasks(late).rows(rows)
                .build();
    }

    private double round(double x) { return Math.round(x * 10.0) / 10.0; }
}