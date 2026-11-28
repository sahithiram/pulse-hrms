package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "candidate_consent")
public class CandidateConsent {
    @Id
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt = LocalDateTime.now();

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public LocalDateTime getConsentedAt() { return consentedAt; }
    public void setConsentedAt(LocalDateTime consentedAt) { this.consentedAt = consentedAt; }
    public LocalDate getRetentionUntil() { return retentionUntil; }
    public void setRetentionUntil(LocalDate retentionUntil) { this.retentionUntil = retentionUntil; }
}
