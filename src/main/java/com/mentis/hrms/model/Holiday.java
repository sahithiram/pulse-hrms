package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UPDATED in Sprint 3 (complete file, ready to overwrite).
 * Changes vs the 2026-11-26 Drive version:
 *  + calendarId  -> column holidays.calendar_id  (V5 migration)
 *  + isHalfDay   -> column holidays.is_half_day  (V5 migration)
 *  + getHolidayType stays a free String (FIXED|OPTIONAL|RESTRICTED recommended) for backward compatibility
 *  + new convenience: isOptional()
 * Nothing removed or renamed; existing HolidayService/Repository untouched.
 */
@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "calendar_id")
    private Long calendarId;

    @Column(name = "holiday_name", nullable = false)
    private String holidayName;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    /** FIXED | OPTIONAL | RESTRICTED recommended (legacy free strings still work). */
    @Column(name = "holiday_type", nullable = false)
    private String holidayType;

    @Column(name = "is_half_day", nullable = false)
    private boolean isHalfDay = false;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public Holiday() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Holiday(String holidayName, LocalDate holidayDate, String holidayType, String description) {
        this.holidayName = holidayName;
        this.holidayDate = holidayDate;
        this.holidayType = holidayType;
        this.description = description;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCalendarId() { return calendarId; }
    public void setCalendarId(Long calendarId) { this.calendarId = calendarId; }

    public String getHolidayName() { return holidayName; }
    public void setHolidayName(String holidayName) { this.holidayName = holidayName; }

    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate holidayDate) { this.holidayDate = holidayDate; }

    public String getHolidayType() { return holidayType; }
    public void setHolidayType(String holidayType) { this.holidayType = holidayType; }

    public boolean isHalfDay() { return isHalfDay; }
    public void setHalfDay(boolean halfDay) { isHalfDay = halfDay; }

    public boolean isOptional() {
        return holidayType != null && holidayType.equalsIgnoreCase("OPTIONAL");
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
