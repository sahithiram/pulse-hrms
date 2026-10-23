package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_approval_requests", indexes = {
        @Index(name = "idx_pm_apr_entity",  columnList = "entity_type,entity_id"),
        @Index(name = "idx_pm_apr_approver",columnList = "approver_id"),
        @Index(name = "idx_pm_apr_status",  columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TASK | PROJECT | BUDGET | TIMESHEET */
    @Column(name = "entity_type", nullable = false, length = 20)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    /** Human title for the inbox row (denormalised for display). */
    @Column(name = "entity_title", length = 300)
    private String entityTitle;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "approver_id", nullable = false)
    private Long approverId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;   // PENDING | APPROVED | REJECTED | CANCELLED

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "decision_note", columnDefinition = "TEXT")
    private String decisionNote;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "priority", length = 20)
    private String priority;  // LOW | NORMAL | HIGH

    // ─── Step 2.2 addition ────────────────────────────────────
    /** JSON payload describing the requested change (project status, new budget, ...). */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "PENDING";
        if (priority == null) priority = "NORMAL";
    }
}