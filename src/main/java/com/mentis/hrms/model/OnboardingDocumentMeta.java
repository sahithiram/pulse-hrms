package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Side table for expiry of expiring onboarding documents (legacy onboarding_documents untouched). */
@Entity
@Table(name = "onboarding_document_meta")
public class OnboardingDocumentMeta {

    @Id
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    @Column(name = "remind_sent")
    private LocalDateTime remindSent;

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate expiresOn) { this.expiresOn = expiresOn; }
    public LocalDateTime getRemindSent() { return remindSent; }
    public void setRemindSent(LocalDateTime remindSent) { this.remindSent = remindSent; }
}
