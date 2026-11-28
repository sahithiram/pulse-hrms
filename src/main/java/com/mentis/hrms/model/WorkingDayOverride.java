package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "working_day_override",
       uniqueConstraints = @UniqueConstraint(name = "uq_wdo_calendar_date",
               columnNames = {"calendar_id", "override_date"}))
public class WorkingDayOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id", nullable = false)
    private Long calendarId;

    @Column(name = "override_date", nullable = false)
    private LocalDate overrideDate;

    /** WORKING|OFF */
    @Column(name = "override_type", nullable = false, length = 10)
    private String overrideType = "WORKING";

    @Column(name = "reason", length = 255)
    private String reason;

    public WorkingDayOverride() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCalendarId() { return calendarId; }
    public void setCalendarId(Long calendarId) { this.calendarId = calendarId; }
    public LocalDate getOverrideDate() { return overrideDate; }
    public void setOverrideDate(LocalDate overrideDate) { this.overrideDate = overrideDate; }
    public String getOverrideType() { return overrideType; }
    public void setOverrideType(String overrideType) { this.overrideType = overrideType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
