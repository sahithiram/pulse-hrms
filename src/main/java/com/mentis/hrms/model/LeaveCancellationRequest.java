package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WAS A 0-BYTE STUB — now implemented (REQ-4-07).
 * Partial cancellation: partialDays holds CSV of ISO dates inside the request range; null = whole request.
 */
@Entity
@Table(name = "leave_cancellation_request")
public class LeaveCancellationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_request_id", nullable = false, length = 50)
    private String leaveRequestId;

    @Column(name = "requested_by", nullable = false, length = 50)
    private String requestedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PENDING"; // PENDING|APPROVED|REJECTED

    @Column(name = "partial_days", length = 500)
    private String partialDays;

    @Column(name = "approver_id", length = 50)
    private String approverId;

    @Column(name = "acted_at")
    private LocalDateTime actedAt;

    // --- helpers (not columns) ---
    @Transient
    public List<java.time.LocalDate> partialDateList() {
        if (partialDays == null || partialDays.isBlank()) return List.of();
        return Arrays.stream(partialDays.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(java.time.LocalDate::parse)
                .collect(Collectors.toList());
    }

    @Transient
    public void setPartialDateList(List<java.time.LocalDate> days) {
        this.partialDays = days == null || days.isEmpty() ? null
                : days.stream().map(java.time.LocalDate::toString).collect(Collectors.joining(","));
    }

    public Long getId() { return id; }
    public String getLeaveRequestId() { return leaveRequestId; }
    public void setLeaveRequestId(String leaveRequestId) { this.leaveRequestId = leaveRequestId; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPartialDays() { return partialDays; }
    public void setPartialDays(String partialDays) { this.partialDays = partialDays; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
}
