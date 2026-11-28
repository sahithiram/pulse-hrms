package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Side table `announcements_policy` — additive only; the legacy `announcements` table is never altered. */
@Entity
@Table(name = "announcements_policy")
public class AnnouncementPolicy {

    @Id
    @Column(name = "announcement_id")
    private Long announcementId;

    @Column(name = "requires_ack", nullable = false)
    private boolean requiresAck = false;

    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    public Long getAnnouncementId() { return announcementId; }
    public void setAnnouncementId(Long announcementId) { this.announcementId = announcementId; }
    public boolean isRequiresAck() { return requiresAck; }
    public void setRequiresAck(boolean requiresAck) { this.requiresAck = requiresAck; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
}
