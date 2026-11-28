package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interview_feedback",
       uniqueConstraints = @UniqueConstraint(name = "uq_feedback", columnNames = {"interview_id", "interviewer_id"}))
public class InterviewFeedback {
    public static final String STRONG_YES = "STRONG_YES";
    public static final String YES = "YES";
    public static final String NO = "NO";
    public static final String STRONG_NO = "STRONG_NO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false)
    private Long interviewId;

    @Column(name = "interviewer_id", nullable = false, length = 50)
    private String interviewerId;

    @Column(name = "competency_ratings", columnDefinition = "TEXT")
    private String competencyRatings;

    @Column(name = "recommendation", nullable = false, length = 12)
    private String recommendation = YES;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getInterviewId() { return interviewId; }
    public void setInterviewId(Long interviewId) { this.interviewId = interviewId; }
    public String getInterviewerId() { return interviewerId; }
    public void setInterviewerId(String interviewerId) { this.interviewerId = interviewerId; }
    public String getCompetencyRatings() { return competencyRatings; }
    public void setCompetencyRatings(String competencyRatings) { this.competencyRatings = competencyRatings; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
