package com.mentis.hrms.service;

import com.mentis.hrms.model.enums.DayType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * REQ-4-08: day count = WorkingDayService over [start,end]; weekly offs/holidays excluded
 * (unless leave type counts them — e.g. maternity). Half-day first/last supported.
 */
@Service
public class LeaveDayCalculator {

    public static final String HALF_FIRST = "FIRST_HALF";
    public static final String HALF_SECOND = "SECOND_HALF";

    @Autowired
    private WorkingDayService workingDayService;

    public static class DayCount {
        public final double days;
        public final List<LocalDate> countedDays;
        public final Map<LocalDate, BigDecimal> fractions;

        DayCount(double days, List<LocalDate> countedDays, Map<LocalDate, BigDecimal> fractions) {
            this.days = days;
            this.countedDays = countedDays;
            this.fractions = fractions;
        }
    }

    /**
     * @param countsWeekendsAndHolidays e.g. maternity — count every calendar day as 1.0
     */
    public DayCount calculate(String employeeId, LocalDate start, LocalDate end,
                              String startHalf, String endHalf,
                              boolean countsWeekendsAndHolidays) {
        if (end.isBefore(start)) throw new IllegalArgumentException("end before start");

        List<LocalDate> counted = new ArrayList<>();
        Map<LocalDate, BigDecimal> fractions = new LinkedHashMap<>();

        List<WorkingDayService.DayInfo> infos = workingDayService.getDayInfos(employeeId, start, end);
        boolean singleDay = start.equals(end);

        for (WorkingDayService.DayInfo di : infos) {
            double frac = 1.0;
            boolean include;
            if (countsWeekendsAndHolidays) {
                include = di.employedOnDate;
            } else {
                include = di.isWorking();
            }
            if (!include) continue;

            // Half-day edges (single day: both halves apply)
            if (di.date.equals(start) && startHalf != null && !startHalf.isBlank()) frac -= 0.5;
            if (di.date.equals(end) && endHalf != null && !endHalf.isBlank()) frac -= 0.5;
            if (singleDay && startHalf != null && endHalf != null && frac == 0.0) {
                // Full-day leave taken as one half + other half marked — interpret as 1.0 day
                frac = 1.0;
            }
            if (frac <= 0.0) continue;
            counted.add(di.date);
            fractions.put(di.date, BigDecimal.valueOf(frac));
        }

        double days = fractions.values().stream().mapToDouble(BigDecimal::doubleValue).sum();
        return new DayCount(days, counted, fractions);
    }
}
