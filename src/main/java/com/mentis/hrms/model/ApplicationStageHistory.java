package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "application_stage_history")
public class ApplicationStageHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "from_stage", length = 24)
    private String fromStage;

    @Column(name = "to_stage", nullable = false, length = 24)
    private String toStage;

    @Column(name = "changed_by", length = 50)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt = LocalDateTime.now();

    @Column(name = "reason", length = 255)
    private String reason;

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getFromStage() { return fromStage; }
    public void setFromStage(String fromStage) { this.fromStage = fromStage; }
    public String getToStage() { return toStage; }
    public void setToStage(String toStage) { this.toStage = toStage; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
