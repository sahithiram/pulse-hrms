package com.mentis.hrms.util;

import com.mentis.hrms.model.enums.WeeklyOffPattern;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Pure weekly-off logic. The ONLY place pattern semantics are defined.
 * Week-of-month occurrence is 1-based: (dayOfMonth - 1) / 7 + 1.
 */
public final class WeeklyOffPatternResolver {

    private WeeklyOffPatternResolver() {}

    public static int occurrenceInMonth(LocalDate date) {
        return (date.getDayOfMonth() - 1) / 7 + 1;
    }

    public static int lastOccurrenceInMonth(LocalDate date) {
        LocalDate last = date.with(TemporalAdjusters.lastDayOfMonth());
        LocalDate lastDow = last.with(TemporalAdjusters.previousOrSame(date.getDayOfWeek()));
        return occurrenceInMonth(lastDow);
    }

    public static boolean matches(WeeklyOffPattern pattern, LocalDate date) {
        if (pattern == null) return false;
        int occ = occurrenceInMonth(date);
        switch (pattern) {
            case ALL: return true;
            case ODD: return occ % 2 == 1;
            case EVEN: return occ % 2 == 0;
            case FIRST: return occ == 1;
            case SECOND: return occ == 2;
            case THIRD: return occ == 3;
            case FOURTH: return occ == 4;
            case FIFTH: return occ == 5;
            case LAST: return occ == lastOccurrenceInMonth(date);
            default: return false;
        }
    }

    public static boolean matches(String patternName, LocalDate date) {
        try {
            return matches(WeeklyOffPattern.valueOf(patternName == null ? "" : patternName.trim().toUpperCase()), date);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static DayOfWeek isoDow(int isoValue) {
        return DayOfWeek.of(isoValue);
    }
}
