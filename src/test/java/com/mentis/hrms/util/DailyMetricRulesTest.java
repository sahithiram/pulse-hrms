package com.mentis.hrms.util;

import com.mentis.hrms.model.Shift;
import com.mentis.hrms.model.enums.DailyMetricStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

/** Pure unit tests for the rules engine — no Spring, no DB. Run: mvn -Dtest=DailyMetricRulesTest test */
class DailyMetricRulesTest {

    private Shift general;
    private Shift night;

    @BeforeEach
    void setUp() {
        general = new Shift();
        general.setName("General");
        general.setStartTime(LocalTime.of(9, 0));
        general.setEndTime(LocalTime.of(18, 0));
        general.setGraceMinutes(15);
        general.setFullDayMinutes(480);
        general.setHalfDayMinutes(240);
        general.setCrossesMidnight(false);
        general.setFlexible(false);

        night = new Shift();
        night.setName("Night");
        night.setStartTime(LocalTime.of(21, 0));
        night.setEndTime(LocalTime.of(6, 0));
        night.setGraceMinutes(15);
        night.setFullDayMinutes(480);
        night.setHalfDayMinutes(240);
        night.setCrossesMidnight(true);
        night.setFlexible(false);
    }

    @Test
    void normalDay_onTime_fullDay() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 0),
                LocalDateTime.of(2026, 11, 26, 18, 0),
                30, BigDecimal.ZERO, true, "WORKING");
        assertEquals(DailyMetricStatus.PRESENT, r.status);
        assertEquals(510, r.netMinutes); // 540 - 30 break
        assertEquals(0, r.lateMinutes);
        assertEquals(0, r.earlyOutMinutes);
    }

    @Test
    void lateWithinGrace_notLate() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 15),
                LocalDateTime.of(2026, 11, 26, 18, 0),
                30, BigDecimal.ZERO, true, "WORKING");
        assertEquals(0, r.lateMinutes);
        assertEquals(DailyMetricStatus.PRESENT, r.status);
    }

    @Test
    void lateBeyondGrace_flagged() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 31),
                LocalDateTime.of(2026, 11, 26, 18, 0),
                30, BigDecimal.ZERO, true, "WORKING");
        assertEquals(16, r.lateMinutes);
    }

    @Test
    void belowHalfDayThreshold_absent() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 0),
                LocalDateTime.of(2026, 11, 26, 12, 0),
                0, BigDecimal.ZERO, true, "WORKING");
        assertEquals(DailyMetricStatus.ABSENT, r.status); // 180 min < 240 half-day threshold
    }

    @Test
    void halfDayThreshold_halfDay() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 0),
                LocalDateTime.of(2026, 11, 26, 13, 30),
                0, BigDecimal.ZERO, true, "WORKING");
        assertEquals(DailyMetricStatus.HALF_DAY, r.status);
        assertEquals(270, r.netMinutes);
    }

    @Test
    void checkInWithoutCheckOut_incomplete() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 0), null,
                0, BigDecimal.ZERO, true, "WORKING");
        assertEquals(DailyMetricStatus.INCOMPLETE, r.status);
        assertTrue(r.incomplete);
    }

    @Test
    void nightShiftAcrossMidnight_present() {
        var r = DailyMetricRules.evaluate(night,
                LocalDateTime.of(2026, 11, 26, 21, 0),
                LocalDateTime.of(2026, 11, 27, 6, 0),
                30, BigDecimal.ZERO, true, "WORKING");
        assertEquals(DailyMetricStatus.PRESENT, r.status);
        assertEquals(510, r.netMinutes);
    }

    @Test
    void fullLeaveDay_onLeave_noPunches() {
        var r = DailyMetricRules.evaluate(general, null, null, 0,
                new BigDecimal("1.00"), true, "WORKING");
        assertEquals(DailyMetricStatus.ON_LEAVE, r.status);
    }

    @Test
    void notEmployedDay_notEmployed() {
        var r = DailyMetricRules.evaluate(general,
                LocalDateTime.of(2026, 11, 26, 9, 0),
                LocalDateTime.of(2026, 11, 26, 18, 0),
                0, BigDecimal.ZERO, false, "NOT_EMPLOYED");
        assertEquals(DailyMetricStatus.NOT_EMPLOYED, r.status);
    }

    @Test
    void flexibleShift_noLateEvenIfAfterTen() {
        Shift flex = new Shift();
        flex.setName("Flexible");
        flex.setStartTime(LocalTime.of(9, 0));
        flex.setEndTime(LocalTime.of(18, 0));
        flex.setGraceMinutes(60);
        flex.setFullDayMinutes(480);
        flex.setHalfDayMinutes(240);
        flex.setFlexible(true);
        var r = DailyMetricRules.evaluate(flex,
                LocalDateTime.of(2026, 11, 26, 10, 30),
                LocalDateTime.of(2026, 11, 26, 19, 30),
                30, BigDecimal.ZERO, true, "WORKING");
        assertEquals(0, r.lateMinutes);
        assertEquals(DailyMetricStatus.PRESENT, r.status);
    }
}
