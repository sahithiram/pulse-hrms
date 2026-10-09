package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @Column(name = "id", nullable = false, unique = true, length = 50)
    private String id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "leave_type", length = 50)
    private String leaveType;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "leave_duration", length = 20)
    private String leaveDuration;

    @Column(name = "half_day_type", length = 20)
    private String halfDayType;

    @Column(name = "total_days")
    private Double totalDays;

    @Column(name = "approval_remarks", length = 500)
    private String approvalRemarks;

    // NEW FIELDS FOR CHAT ACTIONS
    @Column(name = "withdrawn")
    private Boolean withdrawn = false;

    @Column(name = "withdrawn_date")
    private LocalDateTime withdrawnDate;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isEmpty()) {
            this.id = generateLeaveId();
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    private String generateLeaveId() {
        int random = (int) (Math.random() * 10000);
        return String.format("LV-%d-%04d", LocalDate.now().getYear(), random);
    }

    // Constructors
    public LeaveRequest() {}

    // Getters and Setters (existing + new)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedDate() { return approvedDate; }
    public void setApprovedDate(LocalDateTime approvedDate) { this.approvedDate = approvedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getLeaveDuration() { return leaveDuration; }
    public void setLeaveDuration(String leaveDuration) { this.leaveDuration = leaveDuration; }

    public String getHalfDayType() { return halfDayType; }
    public void setHalfDayType(String halfDayType) { this.halfDayType = halfDayType; }

    public Double getTotalDays() { return totalDays; }
    public void setTotalDays(Double totalDays) { this.totalDays = totalDays; }

    public String getApprovalRemarks() { return approvalRemarks; }
    public void setApprovalRemarks(String approvalRemarks) { this.approvalRemarks = approvalRemarks; }

    // NEW GETTERS/SETTERS
    public Boolean getWithdrawn() { return withdrawn; }
    public void setWithdrawn(Boolean withdrawn) { this.withdrawn = withdrawn; }

    public LocalDateTime getWithdrawnDate() { return withdrawnDate; }
    public void setWithdrawnDate(LocalDateTime withdrawnDate) { this.withdrawnDate = withdrawnDate; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }
}
