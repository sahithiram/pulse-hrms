package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "shift")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "crosses_midnight", nullable = false)
    private boolean crossesMidnight = false;

    @Column(name = "grace_minutes", nullable = false)
    private int graceMinutes = 0;

    @Column(name = "full_day_minutes", nullable = false)
    private int fullDayMinutes = 480;

    @Column(name = "half_day_minutes", nullable = false)
    private int halfDayMinutes = 240;

    @Column(name = "min_break_minutes", nullable = false)
    private int minBreakMinutes = 0;

    @Column(name = "overtime_after_minutes", nullable = false)
    private int overtimeAfterMinutes = 0;

    @Column(name = "is_flexible", nullable = false)
    private boolean flexible = false;

    @Column(name = "core_hours_start")
    private LocalTime coreHoursStart;

    @Column(name = "core_hours_end")
    private LocalTime coreHoursEnd;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public boolean isCrossesMidnight() { return crossesMidnight; }
    public void setCrossesMidnight(boolean crossesMidnight) { this.crossesMidnight = crossesMidnight; }
    public int getGraceMinutes() { return graceMinutes; }
    public void setGraceMinutes(int graceMinutes) { this.graceMinutes = graceMinutes; }
    public int getFullDayMinutes() { return fullDayMinutes; }
    public void setFullDayMinutes(int fullDayMinutes) { this.fullDayMinutes = fullDayMinutes; }
    public int getHalfDayMinutes() { return halfDayMinutes; }
    public void setHalfDayMinutes(int halfDayMinutes) { this.halfDayMinutes = halfDayMinutes; }
    public int getMinBreakMinutes() { return minBreakMinutes; }
    public void setMinBreakMinutes(int minBreakMinutes) { this.minBreakMinutes = minBreakMinutes; }
    public int getOvertimeAfterMinutes() { return overtimeAfterMinutes; }
    public void setOvertimeAfterMinutes(int overtimeAfterMinutes) { this.overtimeAfterMinutes = overtimeAfterMinutes; }
    public boolean isFlexible() { return flexible; }
    public void setFlexible(boolean flexible) { this.flexible = flexible; }
    public LocalTime getCoreHoursStart() { return coreHoursStart; }
    public void setCoreHoursStart(LocalTime coreHoursStart) { this.coreHoursStart = coreHoursStart; }
    public LocalTime getCoreHoursEnd() { return coreHoursEnd; }
    public void setCoreHoursEnd(LocalTime coreHoursEnd) { this.coreHoursEnd = coreHoursEnd; }
}
