package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "probation_review")
public class ProbationReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "employee_pk")
    private Long employeePk;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "manager_rating")
    private Integer managerRating;

    @Column(name = "competency_ratings", columnDefinition = "TEXT")
    private String competencyRatings;

    @Column(name = "recommendation", length = 12)
    private String recommendation;   // CONFIRM|EXTEND|TERMINATE

    @Column(name = "extension_days")
    private Integer extensionDays;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "hr_decision", length = 12)
    private String hrDecision;       // APPROVED|REJECTED

    @Column(name = "hr_decider", length = 50)
    private String hrDecider;

    @Column(name = "hr_decided_at")
    private LocalDateTime hrDecidedAt;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PENDING";   // PENDING|DECIDED

    @Column(name = "reminder_30_sent", nullable = false)
    private boolean reminder30Sent = false;

    @Column(name = "reminder_14_sent", nullable = false)
    private boolean reminder14Sent = false;

    @Column(name = "reminder_7_sent", nullable = false)
    private boolean reminder7Sent = false;

    @Column(name = "escalation_sent", nullable = false)
    private boolean escalationSent = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public Long getEmployeePk() { return employeePk; }
    public void setEmployeePk(Long employeePk) { this.employeePk = employeePk; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }
    public Integer getManagerRating() { return managerRating; }
    public void setManagerRating(Integer managerRating) { this.managerRating = managerRating; }
    public String getCompetencyRatings() { return competencyRatings; }
    public void setCompetencyRatings(String competencyRatings) { this.competencyRatings = competencyRatings; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
    public Integer getExtensionDays() { return extensionDays; }
    public void setExtensionDays(Integer extensionDays) { this.extensionDays = extensionDays; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public String getHrDecision() { return hrDecision; }
    public void setHrDecision(String hrDecision) { this.hrDecision = hrDecision; }
    public String getHrDecider() { return hrDecider; }
    public void setHrDecider(String hrDecider) { this.hrDecider = hrDecider; }
    public LocalDateTime getHrDecidedAt() { return hrDecidedAt; }
    public void setHrDecidedAt(LocalDateTime hrDecidedAt) { this.hrDecidedAt = hrDecidedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isReminder30Sent() { return reminder30Sent; }
    public void setReminder30Sent(boolean reminder30Sent) { this.reminder30Sent = reminder30Sent; }
    public boolean isReminder14Sent() { return reminder14Sent; }
    public void setReminder14Sent(boolean reminder14Sent) { this.reminder14Sent = reminder14Sent; }
    public boolean isReminder7Sent() { return reminder7Sent; }
    public void setReminder7Sent(boolean reminder7Sent) { this.reminder7Sent = reminder7Sent; }
    public boolean isEscalationSent() { return escalationSent; }
    public void setEscalationSent(boolean escalationSent) { this.escalationSent = escalationSent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
