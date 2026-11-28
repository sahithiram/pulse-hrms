package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "weekly_off_rule",
       uniqueConstraints = @UniqueConstraint(name = "uq_wor_calendar_day_pattern",
               columnNames = {"calendar_id", "day_of_week", "pattern"}))
public class WeeklyOffRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id", nullable = false)
    private Long calendarId;

    /** ISO day of week: 1=MON ... 7=SUN */
    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    /** ALL|ODD|EVEN|FIRST|SECOND|THIRD|FOURTH|FIFTH|LAST */
    @Column(name = "pattern", nullable = false, length = 10)
    private String pattern = "ALL";

    public WeeklyOffRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCalendarId() { return calendarId; }
    public void setCalendarId(Long calendarId) { this.calendarId = calendarId; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }
}
