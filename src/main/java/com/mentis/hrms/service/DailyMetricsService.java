package com.mentis.hrms.service;

import com.mentis.hrms.model.Attendance;
import com.mentis.hrms.model.EmployeeDailyMetrics;
import com.mentis.hrms.model.EmployeeDailyMetricsId;
import com.mentis.hrms.model.Shift;
import com.mentis.hrms.repository.*;
import com.mentis.hrms.util.DailyMetricRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REQ-5-09 — idempotent recompute of ONE day's metrics. Called by every event source
 * (check-in/out, regularization approval, leave approve/cancel, calendar/shift change).
 */
@Service
public class DailyMetricsService {

    private static final Logger logger = LoggerFactory.getLogger(DailyMetricsService.class);

    @Autowired private EmployeeDailyMetricsRepository metricsRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private WorkingDayService workingDayService;
    @Autowired private ShiftResolver shiftResolver;
    @Autowired(required = false) private LeaveRequestDayReadRepository leaveDayReadRepository;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Value("${hrms.daily-metrics.rule-version:1}")
    private int ruleVersion;

    @Transactional
    public EmployeeDailyMetrics recompute(String employeeId, LocalDate date) {
        WorkingDayService.DayInfo day = workingDayService.getDayInfo(employeeId, date);
        Optional<Shift> shiftOpt = shiftResolver.resolve(employeeId, date);
        Shift shift = shiftOpt.orElse(null);

        List<Attendance> sessions = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, date);

        LocalDateTime firstIn = sessions.stream()
                .map(Attendance::getCheckInTime).filter(t -> t != null).min(LocalDateTime::compareTo).orElse(null);
        LocalDateTime lastOut = sessions.stream()
                .map(Attendance::getCheckOutTime).filter(t -> t != null).max(LocalDateTime::compareTo).orElse(null);
        int breakMinutes = sessions.stream()
                .mapToInt(a -> a.getTotalBreakMinutes() == null ? 0 : a.getTotalBreakMinutes()).sum();

        BigDecimal leaveFraction = BigDecimal.ZERO;
        if (leaveDayReadRepository != null) {
            try {
                leaveFraction = leaveDayReadRepository.sumCountedLeaveFraction(employeeId, date);
                if (leaveFraction == null) leaveFraction = BigDecimal.ZERO;
            } catch (Exception e) {
                logger.warn("Leave fraction lookup failed for {} on {}: {}", employeeId, date, e.getMessage());
            }
        }

        DailyMetricRules.Result r = DailyMetricRules.evaluate(
                shift, firstIn, lastOut, breakMinutes, leaveFraction,
                day.employedOnDate, day.dayType.name());

        EmployeeDailyMetrics m = metricsRepository
                .findById(new EmployeeDailyMetricsId(employeeId, date))
                .orElseGet(EmployeeDailyMetrics::new);
        m.setEmployeeId(employeeId);
        m.setMetricsDate(date);
        m.setDayType(day.dayType.name());
        m.setShiftId(shift != null ? shift.getId() : null);
        m.setStatus(r.status.name());
        m.setFirstIn(firstIn);
        m.setLastOut(lastOut);
        m.setGrossMinutes(r.grossMinutes);
        m.setBreakMinutes(breakMinutes);
        m.setNetMinutes(r.netMinutes);
        m.setExpectedMinutes(r.expectedMinutes);
        m.setLateMinutes(r.lateMinutes);
        m.setEarlyOutMinutes(r.earlyOutMinutes);
        m.setOvertimeMinutes(r.overtimeMinutes);
        m.setLeaveFraction(leaveFraction);
        m.setRegularized(sessions.stream()
                .anyMatch(a -> "REGULARIZED".equalsIgnoreCase(String.valueOf(a.getSource())) || a.isEditedFlag()));
        m.setComputedAt(LocalDateTime.now());
        m.setRuleVersion(ruleVersion);
        metricsRepository.save(m);

        eventPublisher.publishEvent(new EmployeeDailyMetricsChangedEvent(employeeId, date, m.getStatus()));
        return m;
    }

    /** Bulk recompute for backfill/rule changes. Frozen months should be excluded by the caller. */
    @Transactional
    public int recomputeRange(String employeeIdOrNull, LocalDate from, LocalDate to, List<String> employeeIds) {
        int n = 0;
        List<String> targets = employeeIdOrNull != null ? List.of(employeeIdOrNull) : employeeIds;
        for (String emp : targets) {
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                recompute(emp, d);
                n++;
            }
        }
        logger.info("Bulk recompute {} employees × {}..{} → {} rows", targets.size(), from, to, n);
        return n;
    }
}
