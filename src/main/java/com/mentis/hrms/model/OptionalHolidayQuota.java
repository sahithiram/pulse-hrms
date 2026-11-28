package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "optional_holiday_quota",
       uniqueConstraints = @UniqueConstraint(name = "uq_ohq_calendar_year",
               columnNames = {"calendar_id", "year"}))
public class OptionalHolidayQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id", nullable = false)
    private Long calendarId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "max_optional_per_employee", nullable = false)
    private Integer maxOptionalPerEmployee = 2;

    public OptionalHolidayQuota() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCalendarId() { return calendarId; }
    public void setCalendarId(Long calendarId) { this.calendarId = calendarId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getMaxOptionalPerEmployee() { return maxOptionalPerEmployee; }
    public void setMaxOptionalPerEmployee(Integer maxOptionalPerEmployee) { this.maxOptionalPerEmployee = maxOptionalPerEmployee; }
}
