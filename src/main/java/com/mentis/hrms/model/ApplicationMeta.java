package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "application_meta")
public class ApplicationMeta {
    @Id
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "resume_hash", length = 64)
    private String resumeHash;

    @Column(name = "phone_normalized", length = 20)
    private String phoneNormalized;

    @Column(name = "talent_pool", nullable = false)
    private boolean talentPool = false;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getResumeHash() { return resumeHash; }
    public void setResumeHash(String resumeHash) { this.resumeHash = resumeHash; }
    public String getPhoneNormalized() { return phoneNormalized; }
    public void setPhoneNormalized(String phoneNormalized) { this.phoneNormalized = phoneNormalized; }
    public boolean isTalentPool() { return talentPool; }
    public void setTalentPool(boolean talentPool) { this.talentPool = talentPool; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
}
