package com.mentis.hrms.util;

import com.mentis.hrms.model.Shift;
import com.mentis.hrms.model.enums.DailyMetricStatus;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * REQ-5-06/07 — ALL attendance arithmetic lives here (pure, unit-tested). Nothing is hard-coded:
 * grace, thresholds, overtime all come from the Shift.
 */
public final class DailyMetricRules {

    private DailyMetricRules() {}

    public static class Result {
        public final int lateMinutes;
        public final int earlyOutMinutes;
        public final int grossMinutes;
        public final int netMinutes;
        public final int overtimeMinutes;
        public final int expectedMinutes;
        public final DailyMetricStatus status;
        public final boolean incomplete;

        Result(int lateMinutes, int earlyOutMinutes, int grossMinutes, int netMinutes,
               int overtimeMinutes, int expectedMinutes, DailyMetricStatus status, boolean incomplete) {
            this.lateMinutes = lateMinutes;
            this.earlyOutMinutes = earlyOutMinutes;
            this.grossMinutes = grossMinutes;
            this.netMinutes = netMinutes;
            this.overtimeMinutes = overtimeMinutes;
            this.expectedMinutes = expectedMinutes;
            this.status = status;
            this.incomplete = incomplete;
        }
    }

    public static Result evaluate(Shift shift,
                                  LocalDateTime firstIn,
                                  LocalDateTime lastOut,
                                  int breakMinutes,
                                  java.math.BigDecimal leaveFraction,
                                  boolean employed,
                                  String dayType) {
        if (!employed || "NOT_EMPLOYED".equals(dayType)) {
            return new Result(0, 0, 0, 0, 0, 0, DailyMetricStatus.NOT_EMPLOYED, false);
        }
        if ("HOLIDAY".equals(dayType)) {
            return new Result(0, 0, 0, 0, 0, 0, DailyMetricStatus.HOLIDAY, false);
        }
        if ("WEEKLY_OFF".equals(dayType)) {
            return new Result(0, 0, 0, 0, 0, 0, DailyMetricStatus.WEEKLY_OFF, false);
        }

        // Leave covers the day (whole day) → ON_LEAVE regardless of punches
        boolean fullLeave = leaveFraction != null && leaveFraction.compareTo(new java.math.BigDecimal("0.99")) >= 0;
        boolean partialLeave = leaveFraction != null && leaveFraction.signum() > 0 && !fullLeave;
        if (fullLeave) {
            return new Result(0, 0, 0, 0, 0, shift == null ? 480 : shift.getFullDayMinutes(),
                    DailyMetricStatus.ON_LEAVE, false);
        }

        int full = shift != null ? shift.getFullDayMinutes() : 480;
        int half = shift != null ? shift.getHalfDayMinutes() : 240;
        int grace = shift != null ? shift.getGraceMinutes() : 0;
        int minBreak = shift != null ? shift.getMinBreakMinutes() : 0;
        boolean flexible = shift != null && shift.isFlexible();

        if (firstIn == null) {
            // No punch at all — absent (or half-leave day counts as half leave)
            return new Result(0, 0, 0, 0, 0, full,
                    partialLeave ? DailyMetricStatus.HALF_DAY : DailyMetricStatus.ABSENT, false);
        }

        int effectiveBreak = Math.max(breakMinutes, minBreak);

        // LATE — vs shift start + grace (flexible shifts: no lateness, presence judged on net minutes)
        int late = 0;
        if (shift != null && !flexible) {
            int shiftStart = shift.getStartTime().getHour() * 60 + shift.getStartTime().getMinute();
            int inMin = firstIn.getHour() * 60 + firstIn.getMinute();
            late = Math.max(0, inMin - (shiftStart + grace));
        }

        if (lastOut == null) {
            // Check-in without check-out → INCOMPLETE until auto-close/regularization resolves it
            return new Result(late, 0, 0, 0, 0, full, DailyMetricStatus.INCOMPLETE, true);
        }

        long gross = Duration.between(firstIn, lastOut).toMinutes();
        if (gross < 0) gross += 24L * 60; // night shift safety net
        int net = (int) Math.max(0, gross - effectiveBreak);

        // EARLY OUT — vs shift end (waived for flexible shifts that met full-day threshold)
        int earlyOut = 0;
        if (shift != null && !flexible) {
            int shiftEnd = shift.getEndTime().getHour() * 60 + shift.getEndTime().getMinute();
            if (shift.isCrossesMidnight() || shift.getEndTime().isBefore(shift.getStartTime())) {
                shiftEnd += 24 * 60;
            }
            int outMin = lastOut.getHour() * 60 + lastOut.getMinute();
            if (lastOut.toLocalDate().isAfter(firstIn.toLocalDate())) outMin += 24 * 60;
            earlyOut = Math.max(0, shiftEnd - outMin);
        }

        int overtime = Math.max(0, net - full);

        DailyMetricStatus status;
        if (partialLeave) {
            status = net >= half ? DailyMetricStatus.PRESENT : DailyMetricStatus.HALF_DAY;
        } else if (net >= full) {
            status = DailyMetricStatus.PRESENT;
        } else if (net >= half) {
            status = DailyMetricStatus.HALF_DAY;
        } else {
            status = DailyMetricStatus.ABSENT;
        }

        return new Result(late, earlyOut, (int) gross, net, overtime, full, status, false);
    }
}
