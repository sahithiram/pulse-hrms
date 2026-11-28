package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_regularization")
public class AttendanceRegularization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    /** MISSED_CHECKIN|MISSED_CHECKOUT|WRONG_TIME|WORK_FROM_HOME|ON_DUTY */
    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "requested_in")
    private LocalDateTime requestedIn;

    @Column(name = "requested_out")
    private LocalDateTime requestedOut;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "attachment", length = 64)
    private String attachment;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PENDING";

    @Column(name = "approver", length = 50)
    private String approver;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @Version
    @Column(name = "version")
    private Integer version = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public LocalDate getAttendanceDate() { return attendanceDate; }
    public void setAttendanceDate(LocalDate attendanceDate) { this.attendanceDate = attendanceDate; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getRequestedIn() { return requestedIn; }
    public void setRequestedIn(LocalDateTime requestedIn) { this.requestedIn = requestedIn; }
    public LocalDateTime getRequestedOut() { return requestedOut; }
    public void setRequestedOut(LocalDateTime requestedOut) { this.requestedOut = requestedOut; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAttachment() { return attachment; }
    public void setAttachment(String attachment) { this.attachment = attachment; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getApprover() { return approver; }
    public void setApprover(String approver) { this.approver = approver; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
