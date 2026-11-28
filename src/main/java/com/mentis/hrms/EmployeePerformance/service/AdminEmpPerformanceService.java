package com.mentis.hrms.EmployeePerformance.service;

import com.mentis.hrms.EmployeePerformance.model.AdminEmpPerformance;
import com.mentis.hrms.EmployeePerformance.repository.AdminEmpPerformanceRepository;
import com.mentis.hrms.model.Attendance;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.repository.AttendanceRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;
import com.mentis.hrms.service.WorkingDayService;
import org.springframework.beans.factory.annotation.Value;

/**
 * UPDATED in Sprint 3 (complete file, ready to overwrite).
 * Changes vs the 2026-11-26 Drive version:
 *  - BOTH duplicate day-type loops (Sunday-only / Sunday+holiday) DELETED — day typing now comes
 *    from WorkingDayService (the single authoritative source, REQ-3-08).
 *  - HolidayService/Sunday-check code removed; 9:15 late cutoff externalized to
 *    performance.late.hour / performance.late.minute (defaults 9 / 15 — same behaviour).
 *  - Update path now reuses the same stats object (no second scan).
 * Behaviour note: attendanceDenominator now honors weekly-off patterns, holidays (incl. half-day),
 * optional-holiday choices, overrides and employee start/exit dates — matching leave counting in Sprint 4.
 */
@Service
@Transactional
public class AdminEmpPerformanceService {

    private static final Logger logger = LoggerFactory.getLogger(AdminEmpPerformanceService.class);

    @Autowired
    private AdminEmpPerformanceRepository performanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private WorkingDayService workingDayService;

    // ===== UPDATED WEIGHTAGE CONFIGURATION (3 Pillars) =====
    @Value("${performance.weight.attendance:0.35}")
    private double attendanceWeightRaw;
    @Value("${performance.weight.behavior:0.175}")
    private double behaviorWeightRaw;
    @Value("${performance.weight.initiative:0.175}")
    private double initiativeWeightRaw;

    // ===== LATE CUTOFF (externalized Sprint 3; same defaults as before) =====
    @Value("${performance.late.hour:9}")
    private int lateHour;
    @Value("${performance.late.minute:15}")
    private int lateMinute;

    // ===== PERFORMANCE RATING THRESHOLDS =====
    private static final double RATING_OUTSTANDING = 90.0;
    private static final double RATING_EXCEEDS = 75.0;
    private static final double RATING_MEETS = 50.0;

    /**
     * Small holder for attendance stats so this exact working-day/holiday
     * logic lives in ONE place and every caller (score calc, preview endpoint,
     * future reports) reads from it instead of re-deriving their own loop.
     */
    public static class AttendanceStats {
        public int totalWorkingDays;
        public int presentDays;
        public int absentDays;
        public int lateDays;
        public double attendancePercentage;
    }

    /**
     * Working-day-aware attendance stats for one employee/month.
     * Working day = whatever WorkingDayService says for THIS employee
     * (weekly-off pattern, that employee's holiday calendar, optional choices, overrides,
     * start/exit date). Present day = has a check-in on that working day.
     */
    public AttendanceStats getAttendanceStats(String employeeId, int year, int month) {
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = YearMonth.of(year, month).atEndOfMonth();

        List<Attendance> attendances = attendanceRepository.findByEmployeeIdAndAttendanceDateBetween(
                employeeId, startDate, endDate);

        Set<LocalDate> attendanceDays = attendances.stream()
                .filter(a -> a.getCheckInTime() != null)
                .map(Attendance::getAttendanceDate)
                .collect(Collectors.toSet());

        Map<LocalDate, Attendance> firstByDate = new HashMap<>();
        for (Attendance a : attendances) {
            if (a.getAttendanceDate() != null) {
                firstByDate.putIfAbsent(a.getAttendanceDate(), a);
            }
        }

        List<WorkingDayService.DayInfo> dayInfos = workingDayService.getDayInfos(employeeId, startDate, endDate);

        AttendanceStats stats = new AttendanceStats();
        for (WorkingDayService.DayInfo di : dayInfos) {
            if (!di.isWorking()) continue;
            stats.totalWorkingDays++;
            LocalDate day = di.date;
            if (attendanceDays.contains(day)) {
                stats.presentDays++;
                Attendance att = firstByDate.get(day);
                if (att != null && att.getCheckInTime() != null
                        && isLate(att.getCheckInTime().getHour(), att.getCheckInTime().getMinute())) {
                    stats.lateDays++;
                }
            } else {
                stats.absentDays++;
            }
        }

        stats.attendancePercentage = stats.totalWorkingDays == 0
                ? 100.0
                : Math.round((stats.presentDays / (double) stats.totalWorkingDays) * 1000.0) / 10.0;

        return stats;
    }

    private boolean isLate(int hour, int minute) {
        return hour > lateHour || (hour == lateHour && minute > lateMinute);
    }

    /**
     * Calculate attendance score for an employee for a specific month.
     * Working days come from WorkingDayService (REQ-3-08).
     */
    public Double calculateAttendanceScore(String employeeId, int year, int month) {
        try {
            AttendanceStats stats = getAttendanceStats(employeeId, year, month);

            logger.info("Attendance Score for {}: {}% (Present: {}, Absent: {}, Late: {}, Working Days: {})",
                    employeeId, stats.attendancePercentage, stats.presentDays, stats.absentDays,
                    stats.lateDays, stats.totalWorkingDays);

            return stats.attendancePercentage;

        } catch (Exception e) {
            logger.error("Error calculating attendance score for {}: {}", employeeId, e.getMessage());
            return 0.0;
        }
    }

    /**
     * Calculate total performance score with 3 pillars (Attendance, Behavior, Initiative).
     * Reweighted proportionally to the SAME base ratio PerformanceSyncService uses.
     */
    public Double calculateTotalScore(Double attendanceScore, Double behaviorScore, Double initiativeScore) {
        if (attendanceScore == null) attendanceScore = 0.0;
        if (behaviorScore == null) behaviorScore = 0.0;
        if (initiativeScore == null) initiativeScore = 0.0;

        double threePillarSum = attendanceWeightRaw + behaviorWeightRaw + initiativeWeightRaw;

        double total = (attendanceScore * (attendanceWeightRaw / threePillarSum))
                + (behaviorScore * (behaviorWeightRaw / threePillarSum))
                + (initiativeScore * (initiativeWeightRaw / threePillarSum));

        return Math.round(total * 10.0) / 10.0;
    }

    /**
     * Get performance rating based on total score
     */
    public String getPerformanceRating(Double totalScore) {
        if (totalScore == null) return "Needs Improvement";
        if (totalScore >= RATING_OUTSTANDING) return "Outstanding";
        if (totalScore >= RATING_EXCEEDS) return "Exceeds Expectations";
        if (totalScore >= RATING_MEETS) return "Meets Expectations";
        return "Needs Improvement";
    }

    /**
     * Get rating color for UI
     */
    public String getRatingColor(String rating) {
        switch (rating) {
            case "Outstanding": return "#10b981";
            case "Exceeds Expectations": return "#0ea5e9";
            case "Meets Expectations": return "#f59e0b";
            case "Needs Improvement": return "#ef4444";
            default: return "#94a3b8";
        }
    }

    /**
     * Get rating badge class for UI
     */
    public String getRatingBadgeClass(String rating) {
        switch (rating) {
            case "Outstanding": return "rating-outstanding";
            case "Exceeds Expectations": return "rating-exceeds";
            case "Meets Expectations": return "rating-meets";
            case "Needs Improvement": return "rating-pip";
            default: return "";
        }
    }

    /**
     * Calculate and save performance for a single employee.
     * The second duplicate working-day loop is gone — stats come from the same
     * getAttendanceStats() used everywhere else.
     */
    @Transactional
    public AdminEmpPerformance calculateAndSavePerformance(
            String employeeId, int year, int month,
            Double behaviorScore, String behaviorRemarks, Integer behaviorStars,
            Double initiativeScore,
            String reviewedBy) {

        logger.info("Calculating performance for {} - {}/{}", employeeId, month, year);

        // Get employee details
        Optional<Employee> empOpt = employeeRepository.findByEmployeeId(employeeId);
        if (empOpt.isEmpty()) {
            throw new RuntimeException("Employee not found: " + employeeId);
        }
        Employee employee = empOpt.get();

        // Calculate attendance stats ONCE via WorkingDayService (REQ-3-08)
        AttendanceStats stats = getAttendanceStats(employeeId, year, month);
        Double attendanceScore = stats.attendancePercentage;

        // Calculate total score - learning removed
        Double totalScore = calculateTotalScore(attendanceScore, behaviorScore, initiativeScore);

        // Get rating
        String rating = getPerformanceRating(totalScore);

        // Build performance record
        AdminEmpPerformance performance = new AdminEmpPerformance();
        performance.setEmployeeId(employeeId);
        performance.setEmployeeName(employee.getFirstName() + " " + employee.getLastName());
        performance.setDepartment(employee.getDepartment() != null ? employee.getDepartment() : "N/A");
        performance.setReviewMonth(LocalDate.of(year, month, 1));

        performance.setAttendanceScore(attendanceScore);
        performance.setBehaviorScore(behaviorScore != null ? behaviorScore : 0.0);
        performance.setInitiativeScore(initiativeScore != null ? initiativeScore : 0.0);
        performance.setLearningScore(0.0); // Set to 0 for database compatibility
        performance.setTotalScore(totalScore);
        performance.setPerformanceRating(rating);

        performance.setBehaviorRemarks(behaviorRemarks);
        performance.setBehaviorRatingStars(behaviorStars);

        performance.setPresentDays(stats.presentDays);
        performance.setAbsentDays(stats.absentDays);
        performance.setLateDays(stats.lateDays);
        performance.setTotalWorkingDays(stats.totalWorkingDays);
        performance.setAttendancePercentage(stats.attendancePercentage);

        performance.setReviewedBy(reviewedBy);
        performance.setReviewedDate(LocalDateTime.now());

        // Check if already exists and update
        Optional<AdminEmpPerformance> existing = performanceRepository
                .findByEmployeeIdAndReviewMonth(performance.getEmployeeId(), performance.getReviewMonth());

        if (existing.isPresent()) {
            AdminEmpPerformance existingPerf = existing.get();
            existingPerf.setAttendanceScore(performance.getAttendanceScore());
            existingPerf.setBehaviorScore(performance.getBehaviorScore());
            existingPerf.setInitiativeScore(performance.getInitiativeScore());
            existingPerf.setLearningScore(0.0); // Set to 0
            existingPerf.setTotalScore(performance.getTotalScore());
            existingPerf.setPerformanceRating(performance.getPerformanceRating());
            existingPerf.setBehaviorRemarks(performance.getBehaviorRemarks());
            existingPerf.setBehaviorRatingStars(performance.getBehaviorRatingStars());
            existingPerf.setPresentDays(performance.getPresentDays());
            existingPerf.setAbsentDays(performance.getAbsentDays());
            existingPerf.setLateDays(performance.getLateDays());
            existingPerf.setTotalWorkingDays(performance.getTotalWorkingDays());
            existingPerf.setAttendancePercentage(performance.getAttendancePercentage());
            existingPerf.setReviewedBy(performance.getReviewedBy());
            existingPerf.setReviewedDate(performance.getReviewedDate());
            return performanceRepository.save(existingPerf);
        }

        return performanceRepository.save(performance);
    }

    /**
     * Get performance for an employee for a specific month
     */
    public Optional<AdminEmpPerformance> getPerformance(String employeeId, LocalDate reviewMonth) {
        return performanceRepository.findByEmployeeIdAndReviewMonth(employeeId, reviewMonth);
    }

    /**
     * Get all performances for a month
     */
    public List<AdminEmpPerformance> getPerformancesForMonth(int year, int month) {
        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        return performanceRepository.findByReviewMonthOrderByTotalScoreDesc(reviewMonth);
    }

    /**
     * Get employee performance history
     */
    public List<AdminEmpPerformance> getEmployeePerformanceHistory(String employeeId) {
        return performanceRepository.findByEmployeeIdOrderByReviewMonthDesc(employeeId);
    }

    /**
     * Get all distinct months with performance data
     */
    public List<LocalDate> getAvailableMonths() {
        return performanceRepository.findDistinctReviewMonths();
    }

    /**
     * Get performance summary statistics for a month
     */
    public Map<String, Object> getPerformanceSummary(int year, int month) {
        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        List<AdminEmpPerformance> performances = performanceRepository.findByReviewMonthOrderByTotalScoreDesc(reviewMonth);

        Map<String, Object> summary = new HashMap<>();

        if (performances.isEmpty()) {
            summary.put("totalEmployees", 0);
            summary.put("averageScore", 0.0);
            summary.put("ratingsDistribution", new HashMap<>());
            summary.put("topPerformers", new ArrayList<>());
            return summary;
        }

        // Average score
        double avgScore = performances.stream()
                .mapToDouble(AdminEmpPerformance::getTotalScore)
                .average()
                .orElse(0.0);

        // Ratings distribution
        Map<String, Long> ratingsDistribution = new HashMap<>();
        for (AdminEmpPerformance p : performances) {
            String rating = p.getPerformanceRating();
            ratingsDistribution.put(rating, ratingsDistribution.getOrDefault(rating, 0L) + 1);
        }

        // Top 5 performers
        List<Map<String, Object>> topPerformers = new ArrayList<>();
        int count = 0;
        for (AdminEmpPerformance p : performances) {
            if (count >= 5) break;
            Map<String, Object> item = new HashMap<>();
            item.put("employeeId", p.getEmployeeId());
            item.put("employeeName", p.getEmployeeName());
            item.put("department", p.getDepartment());
            item.put("totalScore", p.getTotalScore());
            item.put("rating", p.getPerformanceRating());
            topPerformers.add(item);
            count++;
        }

        summary.put("totalEmployees", performances.size());
        summary.put("averageScore", Math.round(avgScore * 10.0) / 10.0);
        summary.put("ratingsDistribution", ratingsDistribution);
        summary.put("topPerformers", topPerformers);

        return summary;
    }

    /**
     * Delete performance record
     */
    public void deletePerformance(Long id) {
        performanceRepository.deleteById(id);
    }

    /**
     * Delete all performances for a month (bulk delete)
     */
    public void deletePerformancesForMonth(int year, int month) {
        LocalDate reviewMonth = LocalDate.of(year, month, 1);
        List<AdminEmpPerformance> performances = performanceRepository.findByReviewMonthOrderByTotalScoreDesc(reviewMonth);
        performanceRepository.deleteAll(performances);
        logger.info("Deleted {} performance records for {}/{}", performances.size(), month, year);
    }
}
