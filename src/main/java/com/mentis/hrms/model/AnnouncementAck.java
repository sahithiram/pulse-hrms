package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcement_ack",
       uniqueConstraints = @UniqueConstraint(name = "uq_ack_ann_user", columnNames = {"announcement_id", "user_id"}))
public class AnnouncementAck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "announcement_id", nullable = false)
    private Long announcementId;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "acked_at", nullable = false)
    private LocalDateTime ackedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(Long announcementId) { this.announcementId = announcementId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public LocalDateTime getAckedAt() { return ackedAt; }
    public void setAckedAt(LocalDateTime ackedAt) { this.ackedAt = ackedAt; }
}
