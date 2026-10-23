package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Phase 6 — the PM twin of WorkPerformanceService.
 * Produces a 0-100 score from PM tasks (assigned / completed / on-time /
 * priority mix / hours). Used by CombinedPerformanceDTO when an employee
 * has no ClickUp mapping (or as a blend, see Phase 6 notes).
 *
 * Nothing here touches AdminEmpPerformance or WorkPerformanceSnapshot —
 * it only returns a plain result the caller decides what to do with.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PmWorkScoreService {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskTimeLogRepository timeLogRepository;

    public static class PmWorkResult {
        public int assigned;
        public int completed;
        public int onTimeCompleted;
        public double completionRate;
        public double onTimeRate;
        public double priorityScore;
        public double efficiencyScore;
        public double finalScore;
    }

    /** Score one employee over a date window (typically a review month). */
    public PmWorkResult computeForEmployee(Long employeePk, LocalDate from, LocalDate to) {
        PmWorkResult r = new PmWorkResult();

        List<Task> assigned = taskAssignmentRepository.findByEmployeeId(employeePk).stream()
                .map(TaskAssignment::getTaskId)
                .distinct()
                .map(id -> taskRepository.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(t -> {
                    LocalDate anchor = t.getStartDate() != null ? t.getStartDate()
                            : (t.getDueDate() != null ? t.getDueDate() : null);
                    return anchor == null || !anchor.isBefore(from);
                })
                .filter(t -> {
                    LocalDate anchor = t.getDueDate() != null ? t.getDueDate()
                            : (t.getStartDate() != null ? t.getStartDate() : null);
                    return anchor == null || !anchor.isAfter(to);
                })
                .toList();

        r.assigned = assigned.size();
        if (r.assigned == 0) {
            r.finalScore = 0.0;
            return r;
        }

        int completed = 0, onTime = 0;
        double priorityEarned = 0, priorityPossible = 0;

        for (Task t : assigned) {
            double weight = priorityWeight(t);
            priorityPossible += weight;
            if (t.getStatus() == TaskStatus.DONE) {
                completed++;
                priorityEarned += weight;
                if (t.getDueDate() != null && t.getUpdatedAt() != null
                        && !t.getUpdatedAt().toLocalDate().isAfter(t.getDueDate())) {
                    onTime++;
                }
            }
        }

        r.completed = completed;
        r.onTimeCompleted = onTime;
        r.completionRate = round((completed / (double) r.assigned) * 100.0);
        r.onTimeRate = completed > 0 ? round((onTime / (double) completed) * 100.0) : 0.0;
        r.priorityScore = priorityPossible > 0 ? round((priorityEarned / priorityPossible) * 100.0) : 0.0;

        // Efficiency: hours logged vs estimate. Only counted when estimates exist.
        double estHours = 0, loggedHours = 0;
        for (Task t : assigned) {
            if (t.getEstimateHours() != null) estHours += t.getEstimateHours();
        }
        long minutes = timeLogRepository.sumMinutesByEmployeeAndRange(employeePk, from, to);
        loggedHours = minutes / 60.0;
        if (estHours <= 0) {
            r.efficiencyScore = 100.0;
        } else {
            double ratio = loggedHours / estHours;
            r.efficiencyScore = ratio <= 1.0 ? 100.0
                    : Math.max(0, 100.0 - ((ratio - 1.0) * 100));
            r.efficiencyScore = round(r.efficiencyScore);
        }

        // Weighted total (matches application.properties weights)
        double score = r.completionRate * 0.35
                + r.onTimeRate     * 0.25
                + r.priorityScore  * 0.20
                + r.efficiencyScore * 0.20;
        r.finalScore = round(Math.min(100, Math.max(0, score)));
        return r;
    }

    private double priorityWeight(Task t) {
        if (t.getPriority() == null) return 2.0;
        return switch (t.getPriority().name()) {
            case "CRITICAL" -> 4.0;
            case "HIGH"     -> 3.0;
            case "LOW"      -> 1.0;
            default         -> 2.0;
        };
    }

    private double round(double v) { return Math.round(v * 10.0) / 10.0; }
}