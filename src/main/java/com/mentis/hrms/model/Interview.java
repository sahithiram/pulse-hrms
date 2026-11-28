package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview")
public class Interview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "round", nullable = false)
    private int round = 1;

    @Column(name = "interviewers", nullable = false, length = 500)
    private String interviewers;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "mode", nullable = false, length = 10)
    private String mode = "VIDEO";

    @Column(name = "link_location", length = 255)
    private String linkLocation;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "SCHEDULED";

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }
    public String getInterviewers() { return interviewers; }
    public void setInterviewers(String interviewers) { this.interviewers = interviewers; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getLinkLocation() { return linkLocation; }
    public void setLinkLocation(String linkLocation) { this.linkLocation = linkLocation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
