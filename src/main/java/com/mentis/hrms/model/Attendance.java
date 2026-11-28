package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * UPDATED in Sprint 5 (complete file, ready to overwrite).
 * Changes vs the 2026-11-26 Drive version — ALL ADDITIVE, nothing removed:
 *  + source        (WEB|MOBILE|BIOMETRIC|ADMIN|REGULARIZED) default 'WEB'   → column `source`
 *  + ipAddress     → `ip_address`
 *  + latitude/longitude (nullable, e.g. mobile punch)                       → `latitude`, `longitude`
 *  + workMode      (OFFICE|REMOTE|FIELD|...)                                → `work_mode`
 *  + isAutoClosed  (nightly auto check-out flag)                            → `is_auto_closed`
 *  + editedFlag    (set by admin correction/regularization)                  → `edited_flag`
 * Existing table name `attendance`, all columns, constructors, getters kept byte-compatible.
 */
@Entity
@Table(name = "attendance")
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "check_in_time")
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    @Column(name = "break_start_time")
    private LocalDateTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalDateTime breakEndTime;

    @Column(name = "total_break_minutes")
    private Integer totalBreakMinutes = 0;

    @Column(name = "total_working_minutes")
    private Integer totalWorkingMinutes = 0;

    @Column(name = "total_working_hours")
    private String totalWorkingHours;

    @Column(name = "status", length = 20)
    private String status = "OFFLINE";

    @Column(name = "source", length = 12, nullable = false)
    private String source = "WEB";

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "latitude")
    private java.math.BigDecimal latitude;

    @Column(name = "longitude")
    private java.math.BigDecimal longitude;

    @Column(name = "work_mode", length = 16)
    private String workMode;

    @Column(name = "is_auto_closed", nullable = false)
    private boolean autoClosed = false;

    @Column(name = "edited_flag", nullable = false)
    private boolean editedFlag = false;

    @Column(name = "session_number")
    private Integer sessionNumber = 1;

    @Column(name = "is_active_session")
    private Boolean isActiveSession = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    private String currentSessionTime;

    // Constructors
    public Attendance() {}

    public Attendance(String employeeId, LocalDate attendanceDate) {
        this.employeeId = employeeId;
        this.attendanceDate = attendanceDate;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }

    public LocalDateTime getCheckInTime() { return checkInTime; }
    public void setCheckInTime(LocalDateTime checkInTime) { this.checkInTime = checkInTime; }

    public LocalDateTime getCheckOutTime() { return checkOutTime; }
    public void setCheckOutTime(LocalDateTime checkOutTime) { this.checkOutTime = checkOutTime; }

    public LocalDateTime getBreakStartTime() { return breakStartTime; }
    public void setBreakStartTime(LocalDateTime breakStartTime) { this.breakStartTime = breakStartTime; }

    public LocalDateTime getBreakEndTime() { return breakEndTime; }
    public void setBreakEndTime(LocalDateTime breakEndTime) { this.breakEndTime = breakEndTime; }

    public Integer getTotalBreakMinutes() { return totalBreakMinutes; }
    public void setTotalBreakMinutes(Integer totalBreakMinutes) { this.totalBreakMinutes = totalBreakMinutes; }

    public Integer getTotalWorkingMinutes() { return totalWorkingMinutes; }
    public void setTotalWorkingMinutes(Integer totalWorkingMinutes) { this.totalWorkingMinutes = totalWorkingMinutes; }

    public String getTotalWorkingHours() { return totalWorkingHours; }
    public void setTotalWorkingHours(String totalWorkingHours) { this.totalWorkingHours = totalWorkingHours; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public java.math.BigDecimal getLatitude() { return latitude; }
    public void setLatitude(java.math.BigDecimal latitude) { this.latitude = latitude; }

    public java.math.BigDecimal getLongitude() { return longitude; }
    public void setLongitude(java.math.BigDecimal longitude) { this.longitude = longitude; }

    public String getWorkMode() { return workMode; }
    public void setWorkMode(String workMode) { this.workMode = workMode; }

    public boolean isAutoClosed() { return autoClosed; }
    public void setAutoClosed(boolean autoClosed) { this.autoClosed = autoClosed; }

    public boolean isEditedFlag() { return editedFlag; }
    public void setEditedFlag(boolean editedFlag) { this.editedFlag = editedFlag; }

    public Integer getSessionNumber() { return sessionNumber; }
    public void setSessionNumber(Integer sessionNumber) { this.sessionNumber = sessionNumber; }

    public Boolean getIsActiveSession() { return isActiveSession; }
    public void setIsActiveSession(Boolean isActiveSession) { this.isActiveSession = isActiveSession; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getCurrentSessionTime() { return currentSessionTime; }
    public void setCurrentSessionTime(String currentSessionTime) { this.currentSessionTime = currentSessionTime; }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Transient
    public boolean isCheckedOut() {
        return checkOutTime != null;
    }
}
