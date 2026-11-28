package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "onboarding_feedback",
       uniqueConstraints = @UniqueConstraint(name = "uq_onb_fb", columnNames = {"employee_id", "day"}))
public class OnboardingFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "day", nullable = false)
    private int day;

    @Column(name = "ratings_json", columnDefinition = "TEXT")
    private String ratingsJson;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public int getDay() { return day; }
    public void setDay(int day) { this.day = day; }
    public String getRatingsJson() { return ratingsJson; }
    public void setRatingsJson(String ratingsJson) { this.ratingsJson = ratingsJson; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
