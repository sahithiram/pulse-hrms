package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "offer_workflow")
public class OfferWorkflow {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String APPROVED = "APPROVED";
    public static final String SENT = "SENT";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String EXPIRED = "EXPIRED";
    public static final String WITHDRAWN = "WITHDRAWN";
    public static final String REVISED = "REVISED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_letter_id", nullable = false, unique = true)
    private Long offerLetterId;

    @Column(name = "status", nullable = false, length = 16)
    private String status = DRAFT;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "approver_hr", length = 50)
    private String approverHr;

    @Column(name = "approver_budget", length = 50)
    private String approverBudget;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public Long getOfferLetterId() { return offerLetterId; }
    public void setOfferLetterId(Long offerLetterId) { this.offerLetterId = offerLetterId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; this.updatedAt = LocalDateTime.now(); }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getApproverHr() { return approverHr; }
    public void setApproverHr(String approverHr) { this.approverHr = approverHr; }
    public String getApproverBudget() { return approverBudget; }
    public void setApproverBudget(String approverBudget) { this.approverBudget = approverBudget; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
