package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ONE row per employee per day (PK employee_id + metrics_date). THE table Performance reads.
 * Recomputed idempotently by DailyMetricsService; every recompute bumps rule_version if changed.
 */
@Entity
@Table(name = "employee_daily_metrics")
@IdClass(EmployeeDailyMetricsId.class)
public class EmployeeDailyMetrics {

    @Id
    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Id
    @Column(name = "metrics_date", nullable = false)
    private LocalDate metricsDate;

    @Column(name = "day_type", nullable = false, length = 24)
    private String dayType;

    @Column(name = "shift_id")
    private Long shiftId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "first_in")
    private LocalDateTime firstIn;

    @Column(name = "last_out")
    private LocalDateTime lastOut;

    @Column(name = "gross_minutes", nullable = false)
    private int grossMinutes = 0;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 0;

    @Column(name = "net_minutes", nullable = false)
    private int netMinutes = 0;

    @Column(name = "expected_minutes", nullable = false)
    private int expectedMinutes = 480;

    @Column(name = "late_minutes", nullable = false)
    private int lateMinutes = 0;

    @Column(name = "early_out_minutes", nullable = false)
    private int earlyOutMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes = 0;

    @Column(name = "leave_fraction", nullable = false, precision = 3, scale = 2)
    private BigDecimal leaveFraction = BigDecimal.ZERO;

    @Column(name = "is_regularized", nullable = false)
    private boolean regularized = false;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt = LocalDateTime.now();

    @Column(name = "rule_version", nullable = false)
    private int ruleVersion = 1;

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public LocalDate getMetricsDate() { return metricsDate; }
    public void setMetricsDate(LocalDate metricsDate) { this.metricsDate = metricsDate; }
    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getFirstIn() { return firstIn; }
    public void setFirstIn(LocalDateTime firstIn) { this.firstIn = firstIn; }
    public LocalDateTime getLastOut() { return lastOut; }
    public void setLastOut(LocalDateTime lastOut) { this.lastOut = lastOut; }
    public int getGrossMinutes() { return grossMinutes; }
    public void setGrossMinutes(int grossMinutes) { this.grossMinutes = grossMinutes; }
    public int getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(int breakMinutes) { this.breakMinutes = breakMinutes; }
    public int getNetMinutes() { return netMinutes; }
    public void setNetMinutes(int netMinutes) { this.netMinutes = netMinutes; }
    public int getExpectedMinutes() { return expectedMinutes; }
    public void setExpectedMinutes(int expectedMinutes) { this.expectedMinutes = expectedMinutes; }
    public int getLateMinutes() { return lateMinutes; }
    public void setLateMinutes(int lateMinutes) { this.lateMinutes = lateMinutes; }
    public int getEarlyOutMinutes() { return earlyOutMinutes; }
    public void setEarlyOutMinutes(int earlyOutMinutes) { this.earlyOutMinutes = earlyOutMinutes; }
    public int getOvertimeMinutes() { return overtimeMinutes; }
    public void setOvertimeMinutes(int overtimeMinutes) { this.overtimeMinutes = overtimeMinutes; }
    public BigDecimal getLeaveFraction() { return leaveFraction; }
    public void setLeaveFraction(BigDecimal leaveFraction) { this.leaveFraction = leaveFraction; }
    public boolean isRegularized() { return regularized; }
    public void setRegularized(boolean regularized) { this.regularized = regularized; }
    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }
    public int getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(int ruleVersion) { this.ruleVersion = ruleVersion; }
}
