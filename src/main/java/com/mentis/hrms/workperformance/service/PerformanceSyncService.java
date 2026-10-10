
package com.mentis.hrms.workperformance.service;

import com.mentis.hrms.EmployeePerformance.model.AdminEmpPerformance;
import com.mentis.hrms.EmployeePerformance.repository.AdminEmpPerformanceRepository;
import com.mentis.hrms.EmployeePerformance.service.AdminEmpPerformanceService;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.workperformance.dto.CombinedPerformanceDTO;
import com.mentis.hrms.workperformance.model.EmployeeClickUpMapping;
import com.mentis.hrms.workperformance.model.WorkPerformanceSnapshot;
import com.mentis.hrms.workperformance.repository.EmployeeClickUpMappingRepository;
import com.mentis.hrms.workperformance.repository.WorkPerformanceSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Combines HR performance data with ClickUp work-performance data.
 *
 * Reads existing HR performance records and writes ClickUp synchronization
 * results only to WorkPerformanceSnapshot.
 *
 * PM module dependencies are intentionally excluded so this service can
 * compile when the PM package is not included in the deployment source.
 */
@Service
public class PerformanceSyncService {

    private static final Logger logger =
            LoggerFactory.getLogger(PerformanceSyncService.class);

    @Autowired
    private AdminEmpPerformanceRepository adminEmpPerformanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeClickUpMappingRepository mappingRepository;

    @Autowired
    private WorkPerformanceSnapshotRepository snapshotRepository;

    @Autowired
    private WorkPerformanceService workPerformanceService;

    @Autowired
    private AdminEmpPerformanceService adminEmpPerformanceService;

    @Value("${performance.weight.attendance:0.35}")
    private double attendanceWeight;

    @Value("${performance.weight.behavior:0.175}")
    private double behaviorWeight;

    @Value("${performance.weight.initiative:0.175}")
    private double initiativeWeight;

    @Value("${performance.weight.work:0.30}")
    private double workWeight;

    private static final double RATING_OUTSTANDING = 90.0;
    private static final double RATING_EXCEEDS = 75.0;
    private static final double RATING_MEETS = 50.0;

    /**
     * Builds the combined performance view for every employee.
     *
     * Priority:
     * 1. Use a saved ClickUp snapshot when available.
     * 2. Otherwise, calculate the score from Attendance, Behavior,
     *    and Initiative using normalized weights.
     *
     * PM task scoring is deliberately omitted because the PM module is
     * excluded from the deployment source.
     */
    @Transactional(readOnly = true)
    public List<CombinedPerformanceDTO> getCombinedResults(int year, int month) {
        LocalDate reviewMonth = LocalDate.of(year, month, 1);

        List<Employee> employees = employeeRepository.findAll();

        Map<String, AdminEmpPerformance> perfByEmployee =
                adminEmpPerformanceRepository.findAll().stream()
                        .filter(p -> reviewMonth.equals(p.getReviewMonth()))
                        .collect(Collectors.toMap(
                                AdminEmpPerformance::getEmployeeId,
                                p -> p,
                                (first, second) -> first
                        ));

        Set<String> mappedEmployeeIds =
                mappingRepository.findByActiveTrue().stream()
                        .map(EmployeeClickUpMapping::getEmployeeId)
                        .collect(Collectors.toSet());

        Map<String, WorkPerformanceSnapshot> snapshotByEmployee =
                new HashMap<>();

        for (String employeeId : mappedEmployeeIds) {
            snapshotRepository
                    .findByEmployeeIdAndReviewMonth(employeeId, reviewMonth)
                    .ifPresent(snapshot ->
                            snapshotByEmployee.put(employeeId, snapshot));
        }

        double threePillarSum =
                attendanceWeight + behaviorWeight + initiativeWeight;

        List<CombinedPerformanceDTO> results = new ArrayList<>();

        for (Employee employee : employees) {
            String employeeId = employee.getEmployeeId();

            AdminEmpPerformance performance =
                    perfByEmployee.get(employeeId);

            CombinedPerformanceDTO dto = new CombinedPerformanceDTO();

            dto.setEmployeeId(employeeId);
            dto.setEmployeeName(
                    (nullSafe(employee.getFirstName()) + " "
                            + nullSafe(employee.getLastName())).trim()
            );
            dto.setDepartment(employee.getDepartment());

            Double attendanceScore =
                    adminEmpPerformanceService.calculateAttendanceScore(
                            employeeId, year, month);

            Double behaviorScore = performance != null
                    ? performance.getBehaviorScore()
                    : null;

            Double initiativeScore = performance != null
                    ? performance.getInitiativeScore()
                    : null;

            dto.setAttendanceScore(attendanceScore);
            dto.setBehaviorScore(behaviorScore);
            dto.setInitiativeScore(initiativeScore);

            boolean isMapped = mappedEmployeeIds.contains(employeeId);
            dto.setHasClickUpMapping(isMapped);

            WorkPerformanceSnapshot snapshot =
                    snapshotByEmployee.get(employeeId);

            // Priority 1: Use the saved ClickUp snapshot.
            if (isMapped && snapshot != null) {
                dto.setWorkScore(snapshot.getWorkScore());
                dto.setFinalScore(snapshot.getFinalCombinedScore());
                dto.setFinalRating(snapshot.getFinalRating());
                dto.setTotalTasksAssigned(snapshot.getTotalTasksAssigned());
                dto.setCompletedTasks(snapshot.getCompletedTasks());
                dto.setWorkScoreSource("CLICKUP");
                dto.setNeedsSync(false);
            } else {
                // Priority 2: No usable ClickUp snapshot.
                // Reweight the three available HR performance pillars.
                double reweighted = 0.0;

                if (threePillarSum > 0.0) {
                    reweighted =
                            (nz(attendanceScore)
                                    * (attendanceWeight / threePillarSum))
                                    + (nz(behaviorScore)
                                    * (behaviorWeight / threePillarSum))
                                    + (nz(initiativeScore)
                                    * (initiativeWeight / threePillarSum));
                }

                reweighted = Math.round(reweighted * 10.0) / 10.0;

                dto.setWorkScore(null);
                dto.setFinalScore(reweighted);
                dto.setFinalRating(rate(reweighted));
                dto.setWorkScoreSource("NONE");
                dto.setNeedsSync(isMapped);
            }

            results.add(dto);
        }

        return results;
    }

    /**
     * Synchronizes all active ClickUp-mapped employees for a given month.
     *
     * Phase 1: Fetch task lists and collect department task counts.
     * Phase 2: Calculate scores using the complete department peer group.
     */
    @Transactional
    public List<WorkPerformanceSnapshot> syncAll(int year, int month) {
        List<EmployeeClickUpMapping> mappings =
                mappingRepository.findByActiveTrue();

        Map<String, Employee> employeesById =
                employeeRepository.findAll().stream()
                        .collect(Collectors.toMap(
                                Employee::getEmployeeId,
                                employee -> employee,
                                (first, second) -> first
                        ));

        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        long[] window = monthWindow(year, month);

        Map<String, List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO>>
                tasksByEmployee = new HashMap<>();

        Map<String, Map<String, Integer>> deptCountsByEmployee =
                new HashMap<>();

        // Phase 1: Fetch tasks for every mapped employee.
        for (EmployeeClickUpMapping mapping : mappings) {
            String employeeId = mapping.getEmployeeId();

            try {
                List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks =
                        workPerformanceService.fetchTasks(
                                mapping.getClickupUserId(),
                                window[0],
                                window[1]
                        );

                tasksByEmployee.put(employeeId, tasks);

                Employee employee = employeesById.get(employeeId);

                String department = employee != null
                        ? employee.getDepartment()
                        : "Unassigned";

                deptCountsByEmployee
                        .computeIfAbsent(
                                department,
                                ignored -> new HashMap<>()
                        )
                        .put(employeeId, tasks.size());

            } catch (Exception e) {
                logger.error(
                        "Fetch failed for employee {}: {}",
                        employeeId,
                        e.getMessage()
                );
            }
        }

        // Phase 2: Score employees against the complete peer group.
        List<WorkPerformanceSnapshot> results = new ArrayList<>();

        for (EmployeeClickUpMapping mapping : mappings) {
            String employeeId = mapping.getEmployeeId();

            List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks =
                    tasksByEmployee.get(employeeId);

            if (tasks == null) {
                continue;
            }

            Employee employee = employeesById.get(employeeId);

            String department = employee != null
                    ? employee.getDepartment()
                    : "Unassigned";

            List<Integer> peerCounts =
                    deptCountsByEmployee
                            .getOrDefault(
                                    department,
                                    Collections.emptyMap()
                            )
                            .entrySet()
                            .stream()
                            .filter(entry ->
                                    !entry.getKey().equals(employeeId))
                            .map(Map.Entry::getValue)
                            .collect(Collectors.toList());

            try {
                WorkPerformanceSnapshot snapshot =
                        buildSnapshot(
                                employeeId,
                                reviewMonth,
                                employee,
                                tasks,
                                peerCounts
                        );

                results.add(snapshotRepository.save(snapshot));

            } catch (Exception e) {
                logger.error(
                        "Scoring failed for employee {}: {}",
                        employeeId,
                        e.getMessage()
                );
            }
        }

        return results;
    }

    /**
     * Synchronizes one employee on demand.
     */
    @Transactional
    public WorkPerformanceSnapshot syncOne(
            String employeeId,
            int year,
            int month
    ) {
        Optional<EmployeeClickUpMapping> mappingOptional =
                mappingRepository.findByEmployeeId(employeeId);

        if (mappingOptional.isEmpty()) {
            logger.warn(
                    "No ClickUp mapping for employee {} — skipping",
                    employeeId
            );
            return null;
        }

        Employee employee =
                employeeRepository.findByEmployeeId(employeeId)
                        .orElse(null);

        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        long[] window = monthWindow(year, month);

        List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks =
                workPerformanceService.fetchTasks(
                        mappingOptional.get().getClickupUserId(),
                        window[0],
                        window[1]
                );

        String department = employee != null
                ? employee.getDepartment()
                : "Unassigned";

        List<Integer> peerCounts =
                buildDepartmentTaskCounts(
                        department,
                        reviewMonth,
                        employeeId
                );

        WorkPerformanceSnapshot snapshot =
                buildSnapshot(
                        employeeId,
                        reviewMonth,
                        employee,
                        tasks,
                        peerCounts
                );

        return snapshotRepository.save(snapshot);
    }

    /**
     * Builds and persists the ClickUp-derived performance snapshot.
     */
    private WorkPerformanceSnapshot buildSnapshot(
            String employeeId,
            LocalDate reviewMonth,
            Employee employee,
            List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks,
            List<Integer> peerCounts
    ) {
        String department = employee != null
                ? employee.getDepartment()
                : "Unassigned";

        String employeeName = employee != null
                ? (nullSafe(employee.getFirstName()) + " "
                + nullSafe(employee.getLastName())).trim()
                : employeeId;

        WorkPerformanceService.WorkScoreResult workResult =
                workPerformanceService.calculateFairWorkScore(
                        tasks,
                        peerCounts
                );

        // Attendance is system-derived and recalculated for this month.
        Double attendanceScore =
                adminEmpPerformanceService.calculateAttendanceScore(
                        employeeId,
                        reviewMonth.getYear(),
                        reviewMonth.getMonthValue()
                );

        // Behavior and initiative come from HR's saved performance record.
        AdminEmpPerformance existing =
                adminEmpPerformanceRepository
                        .findByEmployeeIdAndReviewMonth(
                                employeeId,
                                reviewMonth
                        )
                        .orElse(null);

        Double behaviorScore = existing != null
                ? existing.getBehaviorScore()
                : null;

        Double initiativeScore = existing != null
                ? existing.getInitiativeScore()
                : null;

        boolean missingHrReview =
                behaviorScore == null || initiativeScore == null;

        double combined;
        String ratingLabel;

        if (missingHrReview) {
            double partialWeightSum = attendanceWeight + workWeight;

            if (partialWeightSum > 0.0) {
                combined = (
                        (nz(attendanceScore) * attendanceWeight)
                                + (workResult.finalWorkScore * workWeight)
                ) / partialWeightSum;
            } else {
                combined = 0.0;
            }

            combined = Math.round(combined * 10.0) / 10.0;
            ratingLabel = "Pending HR Review";

        } else {
            combined =
                    (nz(attendanceScore) * attendanceWeight)
                            + (nz(behaviorScore) * behaviorWeight)
                            + (nz(initiativeScore) * initiativeWeight)
                            + (workResult.finalWorkScore * workWeight);

            combined = Math.round(combined * 10.0) / 10.0;
            ratingLabel = rate(combined);
        }

        WorkPerformanceSnapshot snapshot =
                snapshotRepository
                        .findByEmployeeIdAndReviewMonth(
                                employeeId,
                                reviewMonth
                        )
                        .orElse(new WorkPerformanceSnapshot());

        snapshot.setEmployeeId(employeeId);
        snapshot.setEmployeeName(employeeName);
        snapshot.setDepartment(department);
        snapshot.setReviewMonth(reviewMonth);

        snapshot.setTotalTasksAssigned(workResult.totalTasks);
        snapshot.setCompletedTasks(workResult.completedTasks);
        snapshot.setOnTimeCompleted(workResult.onTimeCompleted);
        snapshot.setCompletionRate(workResult.completionRate);
        snapshot.setOnTimeRate(workResult.onTimeRate);
        snapshot.setPriorityScore(workResult.priorityScore);
        snapshot.setEfficiencyScore(workResult.efficiencyScore);
        snapshot.setWorkloadNormalizedScore(
                workResult.workloadNormalizedScore
        );
        snapshot.setWorkScore(workResult.finalWorkScore);

        snapshot.setAttendanceScoreSnapshot(attendanceScore);
        snapshot.setBehaviorScoreSnapshot(behaviorScore);
        snapshot.setInitiativeScoreSnapshot(initiativeScore);

        snapshot.setFinalCombinedScore(combined);
        snapshot.setFinalRating(ratingLabel);
        snapshot.setSyncedAt(LocalDateTime.now());

        return snapshot;
    }

    /**
     * Gets department task counts from existing snapshots, excluding
     * the employee currently being synchronized.
     */
    private List<Integer> buildDepartmentTaskCounts(
            String department,
            LocalDate reviewMonth,
            String excludeEmployeeId
    ) {
        return snapshotRepository
                .findByDepartmentAndReviewMonth(
                        department,
                        reviewMonth
                )
                .stream()
                .filter(snapshot ->
                        !snapshot.getEmployeeId().equals(excludeEmployeeId))
                .map(WorkPerformanceSnapshot::getTotalTasksAssigned)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String rate(double score) {
        if (score >= RATING_OUTSTANDING) {
            return "Outstanding";
        }
        if (score >= RATING_EXCEEDS) {
            return "Exceeds Expectations";
        }
        if (score >= RATING_MEETS) {
            return "Meets Expectations";
        }
        return "Needs Improvement (PIP)";
    }

    private double nz(Double value) {
        return value == null ? 0.0 : value;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    /**
     * Returns the start and end epoch milliseconds for the specified month.
     */
    private long[] monthWindow(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = YearMonth.of(year, month).atEndOfMonth();

        long startMs = start.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        long endMs = end.atTime(23, 59, 59)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        return new long[]{startMs, endMs};
    }
}
