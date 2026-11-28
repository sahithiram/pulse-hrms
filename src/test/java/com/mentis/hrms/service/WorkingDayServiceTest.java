package com.mentis.hrms.service;

import com.mentis.hrms.model.*;
import com.mentis.hrms.model.enums.DayType;
import com.mentis.hrms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * NOTE: not yet executed in CI — verifies construction logic and documented behaviour with mocked repos.
 * Run with: mvn -Dtest=WorkingDayServiceTest test
 */
class WorkingDayServiceTest {

    private WorkingDayService service;

    private final EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private final WorkCalendarRepository workCalendarRepository = Mockito.mock(WorkCalendarRepository.class);
    private final WeeklyOffRuleRepository weeklyOffRuleRepository = Mockito.mock(WeeklyOffRuleRepository.class);
    private final HolidayCalendarRepository holidayCalendarRepository = Mockito.mock(HolidayCalendarRepository.class);
    private final WorkingDayOverrideRepository overrideRepository = Mockito.mock(WorkingDayOverrideRepository.class);
    private final OptionalHolidayChoiceRepository choiceRepository = Mockito.mock(OptionalHolidayChoiceRepository.class);

    private Employee employee;
    private WorkCalendar defaultCalendar;

    @BeforeEach
    void setUp() {
        service = new WorkingDayService();
        inject(service, "employeeRepository", employeeRepository);
        inject(service, "workCalendarRepository", workCalendarRepository);
        inject(service, "weeklyOffRuleRepository", weeklyOffRuleRepository);
        inject(service, "holidayCalendarRepository", holidayCalendarRepository);
        inject(service, "workingDayOverrideRepository", overrideRepository);
        inject(service, "optionalHolidayChoiceRepository", choiceRepository);
        inject(service, "fullDayHours", 8.0);
        inject(service, "cacheTtlSeconds", 0L);

        employee = new Employee();
        employee.setEmployeeId("EMP-1001");
        employee.setStartDate(LocalDate.of(2024, 1, 1));

        defaultCalendar = new WorkCalendar();
        defaultCalendar.setId(1L);
        defaultCalendar.setName("Default Corporate Calendar");
        defaultCalendar.setDefault(true);
        defaultCalendar.setValidFrom(LocalDate.of(2024, 1, 1));

        WeeklyOffRule sundayAll = new WeeklyOffRule();
        sundayAll.setId(11L);
        sundayAll.setCalendarId(1L);
        sundayAll.setDayOfWeek(7);
        sundayAll.setPattern("ALL");

        when(employeeRepository.findByEmployeeId("EMP-1001")).thenReturn(Optional.of(employee));
        when(workCalendarRepository.findByLocationId(any())).thenReturn(List.of());
        when(workCalendarRepository.findFirstByIsDefaultTrue()).thenReturn(Optional.of(defaultCalendar));
        when(weeklyOffRuleRepository.findByCalendarId(1L)).thenReturn(List.of(sundayAll));
        when(overrideRepository.findByCalendarIdAndOverrideDateBetween(eq(1L), any(), any())).thenReturn(List.of());
        when(holidayCalendarRepository.findByCalendarIdAndHolidayDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(choiceRepository.findByEmployeeId("EMP-1001")).thenReturn(List.of());
    }

    @Test
    void sundayIsWeeklyOff_andWeekdayIsWorking() {
        // 2026-11-01 was a Sunday; 2026-11-02 a Monday
        var sunday = service.getDayInfo("EMP-1001", LocalDate.of(2026, 11, 1));
        var monday = service.getDayInfo("EMP-1001", LocalDate.of(2026, 11, 2));
        assertEquals(DayType.WEEKLY_OFF, sunday.dayType);
        assertEquals(DayType.WORKING, monday.dayType);
        assertEquals(8.0, monday.expectedHours, 0.001);
    }

    @Test
    void holidayBeatsSundayAndCountsOnce() {
        Holiday h = new Holiday();
        h.setId(99L);
        h.setCalendarId(1L);
        h.setHolidayName("Diwali");
        h.setHolidayDate(LocalDate.of(2026, 11, 8)); // Sunday
        h.setHolidayType("FIXED");
        h.setHalfDay(false);
        when(holidayCalendarRepository.findByCalendarIdAndHolidayDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(h));
        var info = service.getDayInfo("EMP-1001", LocalDate.of(2026, 11, 8));
        assertEquals(DayType.HOLIDAY, info.dayType);
        assertEquals("Diwali", info.holidayName);
    }

    @Test
    void halfDayHolidayStillCountsAsHalfWorkingDay() {
        Holiday h = new Holiday();
        h.setId(98L);
        h.setCalendarId(1L);
        h.setHolidayName("Christmas Afternoon");
        h.setHolidayDate(LocalDate.of(2026, 12, 25)); // Friday
        h.setHolidayType("FIXED");
        h.setHalfDay(true);
        when(holidayCalendarRepository.findByCalendarIdAndHolidayDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(h));
        var info = service.getDayInfo("EMP-1001", LocalDate.of(2026, 12, 25));
        assertEquals(DayType.HALF_DAY_HOLIDAY, info.dayType);
        assertEquals(4.0, info.expectedHours, 0.001);
        assertTrue(info.isWorking());
    }

    @Test
    void workingSaturdayOverrideMakesWeeklyOffAWorkingDay() {
        WorkingDayOverride ov = new WorkingDayOverride();
        ov.setId(7L);
        ov.setCalendarId(1L);
        ov.setOverrideDate(LocalDate.of(2026, 11, 28)); // Saturday (not weekly off here) — use a Sunday instead
        ov.setOverrideType("WORKING");
        ov.setReason("Election duty");
        LocalDate sunday = LocalDate.of(2026, 11, 29); // Sunday
        ov.setOverrideDate(sunday);
        when(overrideRepository.findByCalendarIdAndOverrideDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(ov));
        var info = service.getDayInfo("EMP-1001", sunday);
        assertEquals(DayType.WORKING, info.dayType);
        assertEquals("Election duty", info.note);
    }

    @Test
    void notEmployedBeforeStartDate() {
        var info = service.getDayInfo("EMP-1001", LocalDate.of(2023, 12, 25));
        assertEquals(DayType.NOT_EMPLOYED, info.dayType);
        assertFalse(info.employedOnDate);
    }

    @Test
    void countWorkingDaysExcludesSundaysAndHolidays() {
        Holiday h = new Holiday();
        h.setId(97L);
        h.setCalendarId(1L);
        h.setHolidayName("Test Holiday");
        h.setHolidayDate(LocalDate.of(2026, 11, 10)); // Tuesday
        h.setHolidayType("FIXED");
        when(holidayCalendarRepository.findByCalendarIdAndHolidayDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(h));
        long working = service.countWorkingDays("EMP-1001", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));
        // Nov 2026: 30 days, 5 Sundays (1,8,15,22,29) → 25 candidate days; minus 1 holiday → 24
        assertEquals(24, working);
    }

    private static void inject(WorkingDayService target, String field, Object value) {
        try {
            var f = WorkingDayService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
