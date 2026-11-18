package com.mentis.hrms.service;

import com.mentis.hrms.model.LeaveBalance;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.repository.LeaveBalanceRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LeaveBalanceService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveBalanceService.class);

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    // List of leave types to display in UI
    private static final List<String> DISPLAY_LEAVE_TYPES = List.of("sick", "casual", "earned");

    /**
     * Calculate the number of days between two dates (inclusive)
     */
    private Double calculateLeaveDays(LocalDate startDate, LocalDate endDate, String leaveDuration, String halfDayType) {
        if (startDate == null || endDate == null) {
            return 0.0;
        }

        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        if (leaveDuration == null) {
            return (double) daysBetween;
        }

        if ("half_day".equalsIgnoreCase(leaveDuration)) {
            return 0.5;
        } else if ("full_day".equalsIgnoreCase(leaveDuration)) {
            return (double) daysBetween;
        } else {
            return (double) daysBetween;
        }
    }

    /**
     * Get leave balance for an employee - UPDATED: Shows sick+casual total = 19
     */

    // ═════════════════════════════════════════════════════════════════
    // MONTHLY leave balance — SL/CL = 1 each per month, EL = Unlimited
    // ═════════════════════════════════════════════════════════════════
    private static final int MONTHLY_SL_QUOTA = 1;
    private static final int MONTHLY_CL_QUOTA = 1;

    /**
     * Balance for ONE calendar month.
     * year/month are 1-based. Null = current month.
     *
     * Decision A / Option 1: a leave that spans two months contributes
     *   only its in-month portion to each month's "used".
     * Decision B: half-day counts as 0.5.
     */
    public Map<String, Object> getMonthlyBalance(String employeeId, Integer year, Integer month) {
        if (year  == null) year  = LocalDate.now().getYear();
        if (month == null) month = LocalDate.now().getMonthValue();

        Map<String, Object> result = new HashMap<>();
        try {
            LocalDate monthStart = LocalDate.of(year, month, 1);
            LocalDate monthEnd   = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

            List<LeaveRequest> approved =
                    leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, "APPROVED");

            double usedSick = 0.0, usedCasual = 0.0, usedEarned = 0.0;

            for (LeaveRequest lr : approved) {
                if (lr.getStartDate() == null || lr.getEndDate() == null) continue;
                String type = lr.getLeaveType() == null ? "" : lr.getLeaveType().toLowerCase();
                if (lr.getEndDate().isBefore(monthStart) || lr.getStartDate().isAfter(monthEnd)) continue;

                double days = daysInMonth(lr, monthStart, monthEnd);
                switch (type) {
                    case "sick"   -> usedSick   += days;
                    case "casual" -> usedCasual += days;
                    case "earned" -> usedEarned += days;
                    default -> { }
                }
            }

            Map<String, Object> sick = new HashMap<>();
            sick.put("total",     MONTHLY_SL_QUOTA);
            sick.put("used",      round2(usedSick));
            sick.put("remaining", round2(MONTHLY_SL_QUOTA - usedSick));
            sick.put("unlimited", false);
            sick.put("yearlyCap", 12);

            Map<String, Object> casual = new HashMap<>();
            casual.put("total",     MONTHLY_CL_QUOTA);
            casual.put("used",      round2(usedCasual));
            casual.put("remaining", round2(MONTHLY_CL_QUOTA - usedCasual));
            casual.put("unlimited", false);
            casual.put("yearlyCap", 12);

            Map<String, Object> earned = new HashMap<>();
            earned.put("total",     "Unlimited");
            earned.put("used",      round2(usedEarned));
            earned.put("remaining", "Unlimited");
            earned.put("unlimited", true);

            Map<String, Map<String, Object>> balances = new HashMap<>();
            balances.put("sick",   sick);
            balances.put("casual", casual);
            balances.put("earned", earned);

            result.put("success",    true);
            result.put("employeeId", employeeId);
            result.put("year",       year);
            result.put("month",      month);
            result.put("monthLabel", monthStart.getMonth().name());
            result.put("balances",   balances);

            logger.info("Monthly balance {} {}/{}: SL used={}, CL used={}, EL used={}",
                    employeeId, year, month, usedSick, usedCasual, usedEarned);

        } catch (Exception e) {
            logger.error("Error fetching monthly balance for {}: {}", employeeId, e.getMessage(), e);
            result.put("success", false);
            result.put("error",   e.getMessage());
        }
        return result;
    }

    /** Days of one leave that overlap [from, to], half-day aware. */
    private double daysInMonth(LeaveRequest lr, LocalDate from, LocalDate to) {
        LocalDate start = lr.getStartDate().isBefore(from) ? from : lr.getStartDate();
        LocalDate end   = lr.getEndDate().isAfter(to)     ? to   : lr.getEndDate();
        if (end.isBefore(start)) return 0.0;

        if ("half_day".equalsIgnoreCase(lr.getLeaveDuration())
                && lr.getStartDate().equals(lr.getEndDate())) {
            return 0.5;
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        return (double) days;
    }

    private double round2(double d) { return Math.round(d * 100.0) / 100.0; }
    /**
     * Initialize default leave balances for an employee - UPDATED: Earned = 999
     */
    @Transactional
    public void initializeEmployeeBalance(String employeeId, Integer year) {
        try {
            leaveBalanceRepository.initializeEmployeeBalances(employeeId, year);
            logger.info("Initialized leave balances for employee {} for year {}", employeeId, year);
        } catch (Exception e) {
            logger.error("Failed to initialize balances for employee {}: {}", employeeId, e.getMessage());
        }
    }

    /**
     * Check if employee has sufficient leave balance before applying
     * UPDATED: For sick/casual, check their specific balance
     * For earned, ALWAYS return true (unlimited)
     */
    /**
     * Monthly balance check before applying.
     * SL / CL validated month-by-month. EL always passes.
     */
    public boolean hasSufficientBalance(String employeeId, String leaveType,
                                        LocalDate startDate, LocalDate endDate,
                                        String leaveDuration, String halfDayType) {
        String type = leaveType == null ? "" : leaveType.toLowerCase();
        if ("earned".equals(type)) return true;
        if (startDate == null || endDate == null) return false;
        if (endDate.isBefore(startDate)) return false;

        Map<YearMonth, Double> requestedByMonth = new LinkedHashMap<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            double day = ("half_day".equalsIgnoreCase(leaveDuration)
                    && startDate.equals(endDate)) ? 0.5 : 1.0;
            requestedByMonth.merge(YearMonth.from(cursor), day, Double::sum);
            cursor = cursor.plusDays(1);
        }

        List<LeaveRequest> approved =
                leaveRequestRepository.findByEmployeeIdAndStatus(employeeId, "APPROVED");

        for (Map.Entry<YearMonth, Double> e : requestedByMonth.entrySet()) {
            YearMonth ym = e.getKey();
            double want  = e.getValue();
            LocalDate mStart = ym.atDay(1);
            LocalDate mEnd   = ym.atEndOfMonth();

            double used = 0.0;
            for (LeaveRequest lr : approved) {
                if (lr.getStartDate() == null) continue;
                String t = lr.getLeaveType() == null ? "" : lr.getLeaveType().toLowerCase();
                if (!t.equals(type)) continue;
                if (lr.getEndDate().isBefore(mStart) || lr.getStartDate().isAfter(mEnd)) continue;
                used += daysInMonth(lr, mStart, mEnd);
            }

            double cap = "sick".equals(type) ? MONTHLY_SL_QUOTA : MONTHLY_CL_QUOTA;
            if (used + want > cap + 1e-9) {
                logger.info("Monthly cap exceeded for {} {} / {}: used={} + want={} > cap={}",
                        employeeId, type, ym, used, want, cap);
                return false;
            }
        }
        return true;
    }


    /**
     * Update leave balance when leave is approved - UPDATED: Auto-use earned when sick/casual exhausted
     */
    @Transactional
    public boolean deductLeaveBalance(LeaveRequest leaveRequest) {
        try {
            String employeeId = leaveRequest.getEmployeeId();
            String leaveType = leaveRequest.getLeaveType().toLowerCase();
            Integer year = leaveRequest.getStartDate().getYear();

            // Calculate days to deduct
            Double daysToDeduct = leaveRequest.getTotalDays();
            if (daysToDeduct == null || daysToDeduct == 0) {
                daysToDeduct = calculateLeaveDays(
                        leaveRequest.getStartDate(),
                        leaveRequest.getEndDate(),
                        leaveRequest.getLeaveDuration(),
                        leaveRequest.getHalfDayType()
                );
            }

            logger.info("Attempting to deduct {} days from {} balance for employee {}",
                    daysToDeduct, leaveType, employeeId);

            // For earned leaves, always deduct (unlimited)
            if ("earned".equals(leaveType)) {
                int updated = leaveBalanceRepository.addUsedDays(employeeId, leaveType, year, daysToDeduct);
                logger.info("Deducted {} earned days for employee {}", daysToDeduct, employeeId);
                return updated > 0;
            }

            // For sick/casual, check if sufficient balance exists
            Boolean hasBalance = leaveBalanceRepository.hasSufficientBalance(employeeId, leaveType, year, daysToDeduct);

            if (hasBalance != null && hasBalance) {
                // Sufficient balance in sick/casual - deduct normally
                int updated = leaveBalanceRepository.addUsedDays(employeeId, leaveType, year, daysToDeduct);
                logger.info("Deducted {} {} days from {} balance", daysToDeduct, leaveType, employeeId);
                return updated > 0;
            } else {
                // Not enough balance in sick/casual - need to use earned leaves
                logger.info("Insufficient {} balance for employee {}. Will use earned leaves.", leaveType, employeeId);

                // Get current sick balance
                Optional<LeaveBalance> sickBalance = leaveBalanceRepository
                        .findByEmployeeIdAndLeaveTypeAndYear(employeeId, "sick", year);
                Optional<LeaveBalance> casualBalance = leaveBalanceRepository
                        .findByEmployeeIdAndLeaveTypeAndYear(employeeId, "casual", year);

                Double sickRemaining = sickBalance.map(b -> b.getTotalDays() - b.getUsedDays()).orElse(0.0);
                Double casualRemaining = casualBalance.map(b -> b.getTotalDays() - b.getUsedDays()).orElse(0.0);

                Double daysNeeded = daysToDeduct;

                // First, use remaining sick days
                if (sickRemaining > 0 && "sick".equals(leaveType)) {
                    Double useFromSick = Math.min(sickRemaining, daysNeeded);
                    leaveBalanceRepository.addUsedDays(employeeId, "sick", year, useFromSick);
                    daysNeeded -= useFromSick;
                    logger.info("Used {} from sick balance", useFromSick);
                }

                // Then, use remaining casual days
                if (daysNeeded > 0 && "casual".equals(leaveType)) {
                    Double useFromCasual = Math.min(casualRemaining, daysNeeded);
                    leaveBalanceRepository.addUsedDays(employeeId, "casual", year, useFromCasual);
                    daysNeeded -= useFromCasual;
                    logger.info("Used {} from casual balance", useFromCasual);
                }

                // Finally, use earned leaves for remaining days
                if (daysNeeded > 0) {
                    leaveBalanceRepository.addUsedDays(employeeId, "earned", year, daysNeeded);
                    logger.info("Used {} from earned balance", daysNeeded);
                }

                return true;
            }

        } catch (Exception e) {
            logger.error("Error deducting leave balance: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Add back leave balance when leave is cancelled/rejected
     */
    @Transactional
    public boolean addBackLeaveBalance(LeaveRequest leaveRequest) {
        try {
            String employeeId = leaveRequest.getEmployeeId();
            String leaveType = leaveRequest.getLeaveType().toLowerCase();
            Integer year = leaveRequest.getStartDate().getYear();

            Double daysToAdd = leaveRequest.getTotalDays();
            if (daysToAdd == null || daysToAdd == 0) {
                daysToAdd = calculateLeaveDays(
                        leaveRequest.getStartDate(),
                        leaveRequest.getEndDate(),
                        leaveRequest.getLeaveDuration(),
                        leaveRequest.getHalfDayType()
                );
            }

            int updated = leaveBalanceRepository.subtractUsedDays(employeeId, leaveType, year, daysToAdd);

            if (updated > 0) {
                logger.info("Added back {} days to {} balance for employee {}",
                        daysToAdd, leaveType, employeeId);
                return true;
            }
            return false;

        } catch (Exception e) {
            logger.error("Error adding back leave balance: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Recalculate used days from all approved leaves
     */
    @Transactional
    public void recalculateAllBalances(String employeeId, Integer year) {
        try {
            List<LeaveRequest> approvedLeaves = leaveRequestRepository
                    .findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                    .stream()
                    .filter(lr -> "APPROVED".equals(lr.getStatus()) &&
                            lr.getStartDate().getYear() == year)
                    .toList();

            Map<String, Double> usedDaysByType = new HashMap<>();

            for (LeaveRequest leave : approvedLeaves) {
                String leaveType = leave.getLeaveType().toLowerCase();
                Double days = leave.getTotalDays();
                if (days == null || days == 0) {
                    days = calculateLeaveDays(
                            leave.getStartDate(),
                            leave.getEndDate(),
                            leave.getLeaveDuration(),
                            leave.getHalfDayType()
                    );
                }
                usedDaysByType.merge(leaveType, days, Double::sum);
            }

            for (Map.Entry<String, Double> entry : usedDaysByType.entrySet()) {
                Optional<LeaveBalance> balanceOpt = leaveBalanceRepository
                        .findByEmployeeIdAndLeaveTypeAndYear(employeeId, entry.getKey(), year);

                if (balanceOpt.isPresent()) {
                    LeaveBalance balance = balanceOpt.get();
                    balance.setUsedDays(entry.getValue());
                    leaveBalanceRepository.save(balance);
                    logger.info("Updated balance for {} - {}: used days = {}",
                            employeeId, entry.getKey(), entry.getValue());
                }
            }

            logger.info("Recalculated balances for employee {} for year {}", employeeId, year);

        } catch (Exception e) {
            logger.error("Error recalculating balances: {}", e.getMessage(), e);
        }
    }
}