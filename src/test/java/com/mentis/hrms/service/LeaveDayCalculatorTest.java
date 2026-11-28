package com.mentis.hrms.service;

import com.mentis.hrms.model.enums.DayType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * NOTE: not executed yet — run with: mvn -Dtest=LeaveDayCalculatorTest test
 * Mocks WorkingDayService so the test verifies ONLY the half-day edge arithmetic.
 */
class LeaveDayCalculatorTest {

    private LeaveDayCalculator calculator;
    private WorkingDayService workingDayService;

    @BeforeEach
    void setUp() {
        calculator = new LeaveDayCalculator();
        workingDayService = Mockito.mock(WorkingDayService.class);
        try {
            var f = LeaveDayCalculator.class.getDeclaredField("workingDayService");
            f.setAccessible(true);
            f.set(calculator, workingDayService);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<WorkingDayService.DayInfo> days(LocalDate start, int count, DayType type) {
        List<WorkingDayService.DayInfo> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate d = start.plusDays(i);
            out.add(new WorkingDayService.DayInfo(d, type, null, null, 8.0, null, true));
        }
        return out;
    }

    @Test
    void fullWeekCountsAllWorkingDays() {
        LocalDate start = LocalDate.of(2026, 11, 2); // Mon
        when(workingDayService.getDayInfos(eq("E1"), any(), any())).thenReturn(days(start, 5, DayType.WORKING));
        var count = calculator.calculate("E1", start, start.plusDays(4), null, null, false);
        assertEquals(5.0, count.days, 0.001);
        assertEquals(5, count.countedDays.size());
    }

    @Test
    void weekendsAndHolidaysAreExcluded() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        List<WorkingDayService.DayInfo> mixed = new ArrayList<>();
        mixed.add(new WorkingDayService.DayInfo(start, DayType.WORKING, null, null, 8.0, null, true));          // Mon
        mixed.add(new WorkingDayService.DayInfo(start.plusDays(1), DayType.HOLIDAY, "Diwali", null, 0, null, true)); // Tue holiday
        mixed.add(new WorkingDayService.DayInfo(start.plusDays(2), DayType.WORKING, null, null, 8.0, null, true)); // Wed
        mixed.add(new WorkingDayService.DayInfo(start.plusDays(3), DayType.WEEKLY_OFF, null, null, 0, null, true)); // Thu weekly off
        mixed.add(new WorkingDayService.DayInfo(start.plusDays(4), DayType.WORKING, null, null, 8.0, null, true)); // Fri
        when(workingDayService.getDayInfos(eq("E1"), any(), any())).thenReturn(mixed);
        var count = calculator.calculate("E1", start, start.plusDays(4), null, null, false);
        assertEquals(3.0, count.days, 0.001);
    }

    @Test
    void halfDayFirstAndLast() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        when(workingDayService.getDayInfos(eq("E1"), any(), any())).thenReturn(days(start, 3, DayType.WORKING));
        var count = calculator.calculate("E1", start, start.plusDays(2),
                LeaveDayCalculator.HALF_FIRST, LeaveDayCalculator.HALF_SECOND, false);
        // 0.5 + 1.0 + 0.5 = 2.0
        assertEquals(2.0, count.days, 0.001);
    }

    @Test
    void maternityCountsWeekendsAndHolidays() {
        LocalDate start = LocalDate.of(2026, 11, 2);
        List<WorkingDayService.DayInfo> mixed = new ArrayList<>();
        mixed.add(new WorkingDayService.DayInfo(start, DayType.WORKING, null, null, 8.0, null, true));
        mixed.add(new WorkingDayService.DayInfo(start.plusDays(1), DayType.WEEKLY_OFF, null, null, 0, null, true));
        mixed.add(new WorkingDayService.DayInfo(start.plusDays(2), DayType.HOLIDAY, "H", null, 0, null, true));
        when(workingDayService.getDayInfos(eq("E1"), any(), any())).thenReturn(mixed);
        var count = calculator.calculate("E1", start, start.plusDays(2), null, null, true);
        assertEquals(3.0, count.days, 0.001);
    }
}
