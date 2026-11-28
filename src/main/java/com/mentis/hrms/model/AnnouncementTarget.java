package com.mentis.hrms.model;

import jakarta.persistence.*;

/** Side table `announcement_target` — DEPARTMENT|LOCATION|ROLE|INDIVIDUAL (ALL/HR_ONLY/EMPLOYEES_ONLY stay in legacy target_audience). */
@Entity
@Table(name = "announcement_target")
public class AnnouncementTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_value", nullable = false, length = 128)
    private String targetValue;

    public Long getId() { return id; }
    public Long getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(Long announcementId) { this.announcementId = announcementId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetValue() { return targetValue; }
    public void setTargetValue(String targetValue) { this.targetValue = targetValue; }
}
