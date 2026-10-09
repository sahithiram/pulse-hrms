package com.mentis.hrms.workperformance.service;

import com.mentis.hrms.EmployeePerformance.model.AdminEmpPerformance;
import com.mentis.hrms.EmployeePerformance.repository.AdminEmpPerformanceRepository;
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
import java.util.*;
import java.util.stream.Collectors;
import com.mentis.hrms.EmployeePerformance.service.AdminEmpPerformanceService;

/**
 * NEW service — the ONLY place that reads AdminEmpPerformanceRepository /
 * EmployeeRepository (both existing, untouched, read-only here) alongside
 * the new ClickUp-derived data. Writes results only to the new
 * WorkPerformanceSnapshot table.
 *
 * PHASE 6 update: the work pillar now has a fallback. When an employee is
 * NOT mapped to ClickUp, or is mapped but not synced this month, we try the
 * PM task pipeline (PmWorkScoreService) before giving up and reweighting to
 * just the 3 pillars. Nothing here ever writes to admin_emp_performance.
 */
@Service
public class PerformanceSyncService {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceSyncService.class);

    @Autowired private AdminEmpPerformanceRepository adminEmpPerformanceRepository; // existing, read-only
    @Autowired private EmployeeRepository employeeRepository;                       // existing, read-only
    @Autowired private EmployeeClickUpMappingRepository mappingRepository;
    @Autowired private WorkPerformanceSnapshotRepository snapshotRepository;
    @Autowired private WorkPerformanceService workPerformanceService;
    @Autowired private AdminEmpPerformanceService adminEmpPerformanceService;

    // ─── PHASE 6 addition ─────────────────────────────────────
    @Autowired private com.mentis.hrms.pm.service.PmWorkScoreService pmWorkScoreService;

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
     * Builds the merged 4-pillar (or reweighted 3-pillar) view for EVERY
     * employee for a given month — this is what the performance.html table
     * should call.
     *
     * PHASE 6: work pillar is filled from ClickUp (preferred) or PM tasks
     * (fallback) or dropped in favour of reweighting the 3 pillars.
     */
    @Transactional(readOnly = true)
    public List<CombinedPerformanceDTO> getCombinedResults(int year, int month) {
        LocalDate reviewMonth = LocalDate.of(year, month, 1);

        List<Employee> employees = employeeRepository.findAll();
        Map<String, AdminEmpPerformance> perfByEmployee = adminEmpPerformanceRepository
                .findAll().stream()
                .filter(p -> reviewMonth.equals(p.getReviewMonth()))
                .collect(Collectors.toMap(AdminEmpPerformance::getEmployeeId, p -> p, (a, b) -> a));

        Set<String> mappedEmployeeIds = mappingRepository.findByActiveTrue().stream()
                .map(EmployeeClickUpMapping::getEmployeeId)
                .collect(Collectors.toSet());

        Map<String, WorkPerformanceSnapshot> snapshotByEmployee = new HashMap<>();
        for (String empId : mappedEmployeeIds) {
            snapshotRepository.findByEmployeeIdAndReviewMonth(empId, reviewMonth)
                    .ifPresent(s -> snapshotByEmployee.put(empId, s));
        }

        // Weight ratio among Attendance/Behavior/Initiative when Work doesn't apply
        double threePillarSum = attendanceWeight + behaviorWeight + initiativeWeight;

        LocalDate monthStart = reviewMonth;
        LocalDate monthEnd   = YearMonth.from(reviewMonth).atEndOfMonth();

        List<CombinedPerformanceDTO> results = new ArrayList<>();
        for (Employee emp : employees) {
            String empId = emp.getEmployeeId();
            AdminEmpPerformance perf = perfByEmployee.get(empId);

            CombinedPerformanceDTO dto = new CombinedPerformanceDTO();
            dto.setEmployeeId(empId);
            dto.setEmployeeName((nullSafe(emp.getFirstName()) + " " + nullSafe(emp.getLastName())).trim());
            dto.setDepartment(emp.getDepartment());

            Double attendanceScore = adminEmpPerformanceService.calculateAttendanceScore(empId, year, month);
            Double behaviorScore = perf != null ? perf.getBehaviorScore() : null;
            Double initiativeScore = perf != null ? perf.getInitiativeScore() : null;
            dto.setAttendanceScore(attendanceScore);
            dto.setBehaviorScore(behaviorScore);
            dto.setInitiativeScore(initiativeScore);

            boolean isMapped = mappedEmployeeIds.contains(empId);
            dto.setHasClickUpMapping(isMapped);

            WorkPerformanceSnapshot snapshot = snapshotByEmployee.get(empId);

            // ── Priority 1: ClickUp snapshot exists ──
            if (isMapped && snapshot != null) {
                dto.setWorkScore(snapshot.getWorkScore());
                dto.setFinalScore(snapshot.getFinalCombinedScore());
                dto.setFinalRating(snapshot.getFinalRating());
                dto.setTotalTasksAssigned(snapshot.getTotalTasksAssigned());
                dto.setCompletedTasks(snapshot.getCompletedTasks());
                dto.setWorkScoreSource("CLICKUP");
                dto.setNeedsSync(false);
            } else {
                // ── Priority 2: PM task work pillar ──
                Long empPk = emp.getId();
                double pmWorkScore = 0.0;
                Integer pmAssigned = null, pmDone = null, pmOnTime = null;
                boolean hasPmWork = false;

                if (empPk != null) {
                    try {
                        com.mentis.hrms.pm.service.PmWorkScoreService.PmWorkResult pmResult =
                                pmWorkScoreService.computeForEmployee(empPk, monthStart, monthEnd);
                        if (pmResult != null && pmResult.assigned > 0) {
                            pmWorkScore = pmResult.finalScore;
                            pmAssigned = pmResult.assigned;
                            pmDone = pmResult.completed;
                            pmOnTime = pmResult.onTimeCompleted;
                            hasPmWork = true;
                        }
                    } catch (Exception e) {
                        logger.warn("PM work score failed for {}: {}", empId, e.getMessage());
                    }
                }

                if (hasPmWork) {
                    double combined = (nz(attendanceScore) * attendanceWeight)
                            + (nz(behaviorScore) * behaviorWeight)
                            + (nz(initiativeScore) * initiativeWeight)
                            + (pmWorkScore * workWeight);
                    combined = Math.round(combined * 10.0) / 10.0;

                    dto.setWorkScore(pmWorkScore);
                    dto.setFinalScore(combined);
                    dto.setFinalRating(rate(combined));
                    dto.setWorkScoreSource("PM");
                    dto.setPmTasksAssigned(pmAssigned);
                    dto.setPmTasksCompleted(pmDone);
                    dto.setPmTasksOnTime(pmOnTime);
                    dto.setTotalTasksAssigned(pmAssigned);
                    dto.setCompletedTasks(pmDone);
                    dto.setNeedsSync(false);
                } else {
                    // ── Priority 3: no work data at all — reweight 3 pillars ──
                    double reweighted = (nz(attendanceScore) * (attendanceWeight / threePillarSum))
                            + (nz(behaviorScore) * (behaviorWeight / threePillarSum))
                            + (nz(initiativeScore) * (initiativeWeight / threePillarSum));
                    reweighted = Math.round(reweighted * 10.0) / 10.0;

                    dto.setWorkScore(null);
                    dto.setFinalScore(reweighted);
                    dto.setFinalRating(rate(reweighted));
                    dto.setWorkScoreSource("NONE");
                    dto.setNeedsSync(isMapped); // mapped but never synced this month
                }
            }

            results.add(dto);
        }
        return results;
    }

    /**
     * Sync + combine scores for every mapped, active employee for a given month.
     *
     * TWO-PHASE PROCESS — this is what makes it fair:
     *  Phase 1: fetch EVERY mapped employee's raw ClickUp tasks first.
     *  Phase 2: only then compute each employee's score, so each one is
     *           normalized against the FULL, final peer group for their
     *           department — never a partially-built list from mid-loop.
     */
    @Transactional
    public List<WorkPerformanceSnapshot> syncAll(int year, int month) {
        List<EmployeeClickUpMapping> mappings = mappingRepository.findByActiveTrue();
        Map<String, Employee> employeesById = employeeRepository.findAll().stream()
                .collect(Collectors.toMap(Employee::getEmployeeId, e -> e, (a, b) -> a));

        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        long[] window = monthWindow(year, month);

        // ---- Phase 1: fetch all raw tasks up front ----
        Map<String, List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO>> tasksByEmployee = new HashMap<>();
        Map<String, Map<String, Integer>> deptCountsByEmployee = new HashMap<>();

        for (EmployeeClickUpMapping mapping : mappings) {
            try {
                List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks =
                        workPerformanceService.fetchTasks(mapping.getClickupUserId(), window[0], window[1]);
                tasksByEmployee.put(mapping.getEmployeeId(), tasks);

                Employee emp = employeesById.get(mapping.getEmployeeId());
                String dept = emp != null ? emp.getDepartment() : "Unassigned";
                deptCountsByEmployee
                        .computeIfAbsent(dept, d -> new HashMap<>())
                        .put(mapping.getEmployeeId(), tasks.size());
            } catch (Exception e) {
                logger.error("Fetch failed for employee {}: {}", mapping.getEmployeeId(), e.getMessage());
            }
        }

        // ---- Phase 2: score everyone against the complete peer group ----
        List<WorkPerformanceSnapshot> results = new ArrayList<>();
        for (EmployeeClickUpMapping mapping : mappings) {
            String empId = mapping.getEmployeeId();
            List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks = tasksByEmployee.get(empId);
            if (tasks == null) continue;

            Employee emp = employeesById.get(empId);
            String dept = emp != null ? emp.getDepartment() : "Unassigned";
            List<Integer> peerCounts = deptCountsByEmployee.getOrDefault(dept, Collections.emptyMap())
                    .entrySet().stream()
                    .filter(e -> !e.getKey().equals(empId))
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            try {
                WorkPerformanceSnapshot snap = buildSnapshot(empId, reviewMonth, emp, tasks, peerCounts);
                results.add(snapshotRepository.save(snap));
            } catch (Exception e) {
                logger.error("Scoring failed for employee {}: {}", empId, e.getMessage());
            }
        }
        return results;
    }

    /** Sync a single employee on demand. */
    @Transactional
    public WorkPerformanceSnapshot syncOne(String employeeId, int year, int month) {
        Optional<EmployeeClickUpMapping> mappingOpt = mappingRepository.findByEmployeeId(employeeId);
        if (mappingOpt.isEmpty()) {
            logger.warn("No ClickUp mapping for employee {} — skipping", employeeId);
            return null;
        }

        Employee employee = employeeRepository.findByEmployeeId(employeeId).orElse(null);
        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        long[] window = monthWindow(year, month);

        List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks =
                workPerformanceService.fetchTasks(mappingOpt.get().getClickupUserId(), window[0], window[1]);

        String dept = employee != null ? employee.getDepartment() : "Unassigned";
        List<Integer> peerCounts = buildDepartmentTaskCounts(dept, reviewMonth, employeeId);

        WorkPerformanceSnapshot snap = buildSnapshot(employeeId, reviewMonth, employee, tasks, peerCounts);
        return snapshotRepository.save(snap);
    }

    private WorkPerformanceSnapshot buildSnapshot(String employeeId, LocalDate reviewMonth, Employee employee,
                                                  List<com.mentis.hrms.workperformance.dto.ClickUpTaskDTO> tasks,
                                                  List<Integer> peerCounts) {

        String department = employee != null ? employee.getDepartment() : "Unassigned";
        String employeeName = employee != null
                ? (nullSafe(employee.getFirstName()) + " " + nullSafe(employee.getLastName())).trim()
                : employeeId;

        WorkPerformanceService.WorkScoreResult workResult =
                workPerformanceService.calculateFairWorkScore(tasks, peerCounts);

        // ATTENDANCE is system-derived — always recompute live.
        Double attendanceScore = adminEmpPerformanceService.calculateAttendanceScore(
                employeeId, reviewMonth.getYear(), reviewMonth.getMonthValue());

        // BEHAVIOR and INITIATIVE come from HR's saved record.
        AdminEmpPerformance existing = adminEmpPerformanceRepository
                .findByEmployeeIdAndReviewMonth(employeeId, reviewMonth)
                .orElse(null);

        Double behaviorScore = existing != null ? existing.getBehaviorScore() : null;
        Double initiativeScore = existing != null ? existing.getInitiativeScore() : null;
        boolean missingHrReview = (behaviorScore == null || initiativeScore == null);

        double combined;
        String ratingLabel;

        if (missingHrReview) {
            double partialWeightSum = attendanceWeight + workWeight;
            combined = Math.round(
                    (((nz(attendanceScore) * attendanceWeight) + (workResult.finalWorkScore * workWeight))
                            / partialWeightSum) * 10.0
            ) / 10.0;
            ratingLabel = "Pending HR Review";
        } else {
            combined = (nz(attendanceScore) * attendanceWeight)
                    + (nz(behaviorScore) * behaviorWeight)
                    + (nz(initiativeScore) * initiativeWeight)
                    + (workResult.finalWorkScore * workWeight);
            combined = Math.round(combined * 10.0) / 10.0;
            ratingLabel = rate(combined);
        }

        WorkPerformanceSnapshot snapshot = snapshotRepository
                .findByEmployeeIdAndReviewMonth(employeeId, reviewMonth)
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
        snapshot.setWorkloadNormalizedScore(workResult.workloadNormalizedScore);
        snapshot.setWorkScore(workResult.finalWorkScore);
        snapshot.setAttendanceScoreSnapshot(attendanceScore);
        snapshot.setBehaviorScoreSnapshot(behaviorScore);
        snapshot.setInitiativeScoreSnapshot(initiativeScore);
        snapshot.setFinalCombinedScore(combined);
        snapshot.setFinalRating(ratingLabel);
        snapshot.setSyncedAt(LocalDateTime.now());

        return snapshot;
    }

    private List<Integer> buildDepartmentTaskCounts(String department, LocalDate reviewMonth, String excludeEmployeeId) {
        return snapshotRepository.findByDepartmentAndReviewMonth(department, reviewMonth).stream()
                .filter(s -> !s.getEmployeeId().equals(excludeEmployeeId))
                .map(WorkPerformanceSnapshot::getTotalTasksAssigned)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private String rate(double score) {
        if (score >= RATING_OUTSTANDING) return "Outstanding";
        if (score >= RATING_EXCEEDS) return "Exceeds Expectations";
        if (score >= RATING_MEETS) return "Meets Expectations";
        return "Needs Improvement (PIP)";
    }

    private double nz(Double v) { return v == null ? 0.0 : v; }
    private String nullSafe(String s) { return s == null ? "" : s; }

    /** Returns {startEpochMs, endEpochMs} for the given month. */
    private long[] monthWindow(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = YearMonth.of(year, month).atEndOfMonth();
        long startMs = start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endMs = end.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new long[]{startMs, endMs};
    }
}
