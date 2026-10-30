package com.mentis.hrms.pm.util;

import com.mentis.hrms.model.Holiday;
import com.mentis.hrms.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class WorkCalendar {
    private final HolidayRepository holidayRepository;

    @Value("${hrms.workweek.off-days:SUNDAY}")
    private Set<DayOfWeek> offDays;
    @Value("${hrms.workday.hours:8}")
    private double hoursPerDay;

    public double hoursPerDay() { return hoursPerDay; }

    public Set<LocalDate> holidays(LocalDate from, LocalDate to) {
        return holidayRepository.findByHolidayDateBetween(from, to).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());
    }
    public boolean isWeeklyOff(LocalDate d) {
        return offDays.contains(d.getDayOfWeek());
    }
    public boolean isWorkingDay(LocalDate d, Set<LocalDate> holidays) {
        return !offDays.contains(d.getDayOfWeek()) && (holidays == null || !holidays.contains(d));
    }

    public int workingDays(LocalDate from, LocalDate to, Set<LocalDate> holidays) {
        if (from == null || to == null || to.isBefore(from)) return 0;
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1))
            if (isWorkingDay(d, holidays)) n++;
        return n;
    }
}