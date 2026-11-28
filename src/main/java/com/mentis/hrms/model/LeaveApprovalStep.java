package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_approval_step",
       uniqueConstraints = @UniqueConstraint(name = "uq_las_step", columnNames = {"request_id", "step_no"}))
public class LeaveApprovalStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "step_no", nullable = false)
    private Integer stepNo;

    @Column(name = "approver_id", nullable = false, length = 50)
    private String approverId;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PENDING"; // PENDING|APPROVED|REJECTED|SKIPPED

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    @Column(name = "remarks", length = 255)
    private String remarks;

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Integer getStepNo() { return stepNo; }
    public void setStepNo(Integer stepNo) { this.stepNo = stepNo; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
}
