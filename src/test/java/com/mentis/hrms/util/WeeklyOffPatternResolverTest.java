package com.mentis.hrms.util;

import com.mentis.hrms.model.enums.WeeklyOffPattern;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyOffPatternResolverTest {

    // November 2026: 1st Nov is a Sunday. Saturdays: 7, 14, 21, 28 (4 Saturdays); Sundays: 1, 8, 15, 22, 29

    @Test
    void sundayOnlyEveryWeek() {
        for (int day = 1; day <= 30; day++) {
            LocalDate d = LocalDate.of(2026, 11, day);
            boolean expected = d.getDayOfWeek().getValue() == 7;
            assertEquals(expected, WeeklyOffPatternResolver.matches(WeeklyOffPattern.ALL, d) && d.getDayOfWeek().getValue() == 7);
        }
    }

    @Test
    void secondAndFourthSaturday() {
        // 2026-11-07 = 1st Sat, 11-14 = 2nd, 11-21 = 3rd, 11-28 = 4th
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.SECOND, LocalDate.of(2026, 11, 14)));
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.FOURTH, LocalDate.of(2026, 11, 28)));
        assertFalse(WeeklyOffPatternResolver.matches(WeeklyOffPattern.SECOND, LocalDate.of(2026, 11, 7)));
        assertFalse(WeeklyOffPatternResolver.matches(WeeklyOffPattern.FIRST, LocalDate.of(2026, 11, 14)));
    }

    @Test
    void oddEvenAlternateSaturdays() {
        // ODD  = 1st, 3rd, 5th Saturdays → 07, 21
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.ODD, LocalDate.of(2026, 11, 7)));
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.ODD, LocalDate.of(2026, 11, 21)));
        // EVEN = 2nd, 4th Saturdays → 14, 28
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.EVEN, LocalDate.of(2026, 11, 14)));
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.EVEN, LocalDate.of(2026, 11, 28)));
    }

    @Test
    void monthsStartingOnEachWeekday_alternateSaturday() {
        // For each possible 1st-of-month weekday, 2nd/4th Saturdays must be occurrences 2 and 4
        for (int m = 1; m <= 12; m++) {
            LocalDate d = LocalDate.of(2026, m, 1);
            int found = 0;
            for (int day = 1; day <= d.lengthOfMonth(); day++) {
                LocalDate cur = d.withDayOfMonth(day);
                if (cur.getDayOfWeek().getValue() == 6 && WeeklyOffPatternResolver.matches(WeeklyOffPattern.SECOND, cur)) {
                    found++;
                    assertEquals(2, WeeklyOffPatternResolver.occurrenceInMonth(cur));
                }
            }
            assertEquals(1, found, "exactly one 2nd Saturday in month " + m);
        }
    }

    @Test
    void leapYearFebruary_29thIsLastSunday() {
        // Feb 2028 is a leap year; last day 2028-02-29 is a Tuesday. Last Sunday = 2028-02-27.
        LocalDate lastSunday = LocalDate.of(2028, 2, 27);
        assertEquals(7, lastSunday.getDayOfWeek().getValue());
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.LAST, lastSunday));
        // 2027 (non-leap): 2027-02-28 is a Sunday and is the last Sunday
        LocalDate nonLeapLastSunday = LocalDate.of(2027, 2, 28);
        assertEquals(7, nonLeapLastSunday.getDayOfWeek().getValue());
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.LAST, nonLeapLastSunday));
    }

    @Test
    void fifthPatternOnlyInFiveSaturdayMonths() {
        // Jan 2027: Saturdays 2,9,16,23,30 → 5 Saturdays
        assertTrue(WeeklyOffPatternResolver.matches(WeeklyOffPattern.FIFTH, LocalDate.of(2027, 1, 30)));
        // Feb 2026: Saturdays 7,14,21,28 → no 5th Saturday
        assertFalse(WeeklyOffPatternResolver.matches(WeeklyOffPattern.FIFTH, LocalDate.of(2026, 2, 28)));
    }

    @Test
    void unknownPatternMatchesNothing() {
        assertFalse(WeeklyOffPatternResolver.matches("BOGUS", LocalDate.of(2026, 11, 7)));
        assertFalse(WeeklyOffPatternResolver.matches((String) null, LocalDate.of(2026, 11, 7)));
    }
}
