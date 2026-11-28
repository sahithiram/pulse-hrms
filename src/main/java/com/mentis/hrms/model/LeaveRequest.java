package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * UPDATED in Sprint 4 (complete file, ready to overwrite).
 * Changes vs the 2026-11-26 Drive version:
 *  + @Version Long version          → column leave_requests.version (optimistic locking, concurrent approvals)
 *  + Long leaveTypeId               → column leave_type_id (FK → leave_type.id) alongside legacy free-text leaveType (kept)
 *  + String startHalf / endHalf     → FIRST_HALF | SECOND_HALF
 *  + String currentApproverId       → who the request is waiting on
 *  + String attachmentId            → supporting document ref
 *  - generateLeaveId() no longer uses Math.random() — PrePersist assigns a UUID-based placeholder;
 *    REAL codes (LV-YYYY-NNNNNN, per-year sequence) are assigned by LeaveCodeGenerator at service level.
 *  Status values now may include: DRAFT, PENDING, APPROVED, REJECTED, WITHDRAWN, CANCELLATION_PENDING, CANCELLED.
 */
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

    @Column(name = "start_half", length = 10)
    private String startHalf;

    @Column(name = "end_half", length = 10)
    private String endHalf;

    @Column(name = "leave_type", length = 50)
    private String leaveType;

    @Column(name = "leave_type_id")
    private Long leaveTypeId;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", length = 20)
    private String status = "PENDING";

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "current_approver_id", length = 50)
    private String currentApproverId;

    @Column(name = "approved_date")
    private LocalDateTime approvedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "attachment_id", length = 64)
    private String attachmentId;

    @Column(name = "leave_duration", length = 20)
    private String leaveDuration;

    @Column(name = "half_day_type", length = 20)
    private String halfDayType;

    @Column(name = "total_days")
    private Double totalDays;

    @Column(name = "approval_remarks", length = 500)
    private String approvalRemarks;

    @Column(name = "withdrawn")
    private Boolean withdrawn = false;

    @Column(name = "withdrawn_date")
    private LocalDateTime withdrawnDate;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Version
    @Column(name = "version")
    private Long version = 0L;

    @PrePersist
    public void prePersist() {
        if (this.id == null || this.id.isEmpty()) {
            // Placeholder — services SHOULD set the real LV-YYYY-NNNNNN code via LeaveCodeGenerator before save.
            this.id = "LV-TMP-" + UUID.randomUUID().toString().substring(0, 8);
        }
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.version == null) {
            this.version = 0L;
        }
    }

    public LeaveRequest() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public String getStartHalf() { return startHalf; }
    public void setStartHalf(String startHalf) { this.startHalf = startHalf; }

    public String getEndHalf() { return endHalf; }
    public void setEndHalf(String endHalf) { this.endHalf = endHalf; }

    public String getLeaveType() { return leaveType; }
    public void setLeaveType(String leaveType) { this.leaveType = leaveType; }

    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getCurrentApproverId() { return currentApproverId; }
    public void setCurrentApproverId(String currentApproverId) { this.currentApproverId = currentApproverId; }

    public LocalDateTime getApprovedDate() { return approvedDate; }
    public void setApprovedDate(LocalDateTime approvedDate) { this.approvedDate = approvedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getAttachmentId() { return attachmentId; }
    public void setAttachmentId(String attachmentId) { this.attachmentId = attachmentId; }

    public String getLeaveDuration() { return leaveDuration; }
    public void setLeaveDuration(String leaveDuration) { this.leaveDuration = leaveDuration; }

    public String getHalfDayType() { return halfDayType; }
    public void setHalfDayType(String halfDayType) { this.halfDayType = halfDayType; }

    public Double getTotalDays() { return totalDays; }
    public void setTotalDays(Double totalDays) { this.totalDays = totalDays; }

    public String getApprovalRemarks() { return approvalRemarks; }
    public void setApprovalRemarks(String approvalRemarks) { this.approvalRemarks = approvalRemarks; }

    public Boolean getWithdrawn() { return withdrawn; }
    public void setWithdrawn(Boolean withdrawn) { this.withdrawn = withdrawn; }

    public LocalDateTime getWithdrawnDate() { return withdrawnDate; }
    public void setWithdrawnDate(LocalDateTime withdrawnDate) { this.withdrawnDate = withdrawnDate; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
