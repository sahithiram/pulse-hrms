package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "resignation")
public class Resignation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "submitted_on", nullable = false)
    private LocalDate submittedOn;

    @Column(name = "requested_last_day", nullable = false)
    private LocalDate requestedLastDay;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "SUBMITTED";   // SUBMITTED|ACCEPTED|REVOKED|COMPLETED

    @Column(name = "accepted_last_day")
    private LocalDate acceptedLastDay;

    @Column(name = "notice_days_required", nullable = false)
    private int noticeDaysRequired;

    @Column(name = "notice_shortfall")
    private Integer noticeShortfall;

    @Column(name = "notice_buyout", nullable = false)
    private boolean noticeBuyout = false;

    @Column(name = "prev_employment_status", length = 20)
    private String prevEmploymentStatus;

    @Column(name = "submitted_by", length = 50)
    private String submittedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 255)
    private String revokeReason;

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public LocalDate getSubmittedOn() { return submittedOn; }
    public void setSubmittedOn(LocalDate submittedOn) { this.submittedOn = submittedOn; }
    public LocalDate getRequestedLastDay() { return requestedLastDay; }
    public void setRequestedLastDay(LocalDate requestedLastDay) { this.requestedLastDay = requestedLastDay; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getAcceptedLastDay() { return acceptedLastDay; }
    public void setAcceptedLastDay(LocalDate acceptedLastDay) { this.acceptedLastDay = acceptedLastDay; }
    public int getNoticeDaysRequired() { return noticeDaysRequired; }
    public void setNoticeDaysRequired(int noticeDaysRequired) { this.noticeDaysRequired = noticeDaysRequired; }
    public Integer getNoticeShortfall() { return noticeShortfall; }
    public void setNoticeShortfall(Integer noticeShortfall) { this.noticeShortfall = noticeShortfall; }
    public boolean isNoticeBuyout() { return noticeBuyout; }
    public void setNoticeBuyout(boolean noticeBuyout) { this.noticeBuyout = noticeBuyout; }
    public String getPrevEmploymentStatus() { return prevEmploymentStatus; }
    public void setPrevEmploymentStatus(String prevEmploymentStatus) { this.prevEmploymentStatus = prevEmploymentStatus; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokeReason() { return revokeReason; }
    public void setRevokeReason(String revokeReason) { this.revokeReason = revokeReason; }
}
