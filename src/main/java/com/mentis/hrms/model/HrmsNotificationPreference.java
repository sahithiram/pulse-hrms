package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "hrms_notification_preference",
       uniqueConstraints = @UniqueConstraint(name = "uq_hnp_user_event", columnNames = {"user_id", "event_type"}))
public class HrmsNotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Column(name = "in_app", nullable = false)
    private boolean inApp = true;

    @Column(name = "email", nullable = false)
    private boolean email = true;

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public boolean isInApp() { return inApp; }
    public void setInApp(boolean inApp) { this.inApp = inApp; }
    public boolean isEmail() { return email; }
    public void setEmail(boolean email) { this.email = email; }
}
