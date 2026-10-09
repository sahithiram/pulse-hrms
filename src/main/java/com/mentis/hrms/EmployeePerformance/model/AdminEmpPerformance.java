package com.mentis.hrms.EmployeePerformance.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_emp_performance")
public class AdminEmpPerformance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private String employeeId;

    @Column(name = "employee_name")
    private String employeeName;

    @Column(name = "department")
    private String department;

    @Column(name = "review_month", nullable = false)
    private LocalDate reviewMonth;

    // ===== SCORES (Out of 100) =====
    @Column(name = "attendance_score")
    private Double attendanceScore;      // 40% weight - Auto calculated

    @Column(name = "behavior_score")
    private Double behaviorScore;        // 20% weight - Manual input

    @Column(name = "initiative_score")
    private Double initiativeScore;      // 20% weight - Manual input

    @Column(name = "learning_score")
    private Double learningScore;        // 20% weight - Manual input

    @Column(name = "total_score")
    private Double totalScore;           // Weighted sum

    // ===== PERFORMANCE RATING =====
    @Column(name = "performance_rating", length = 30)
    private String performanceRating;    // Outstanding / Exceeds / Meets / PIP

    // ===== BEHAVIOR DETAILS =====
    @Column(name = "behavior_remarks", columnDefinition = "TEXT")
    private String behaviorRemarks;

    @Column(name = "behavior_rating_stars")
    private Integer behaviorRatingStars; // 1-5 stars

    // ===== ATTENDANCE DETAILS =====
    @Column(name = "present_days")
    private Integer presentDays;

    @Column(name = "absent_days")
    private Integer absentDays;

    @Column(name = "late_days")
    private Integer lateDays;

    @Column(name = "total_working_days")
    private Integer totalWorkingDays;

    @Column(name = "attendance_percentage")
    private Double attendancePercentage;

    // ===== AUDIT FIELDS =====
    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_date")
    private LocalDateTime reviewedDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== CONSTRUCTORS =====
    public AdminEmpPerformance() {}

    public AdminEmpPerformance(String employeeId, LocalDate reviewMonth) {
        this.employeeId = employeeId;
        this.reviewMonth = reviewMonth;
    }

    // ===== GETTERS & SETTERS =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDate getReviewMonth() { return reviewMonth; }
    public void setReviewMonth(LocalDate reviewMonth) { this.reviewMonth = reviewMonth; }

    public Double getAttendanceScore() { return attendanceScore; }
    public void setAttendanceScore(Double attendanceScore) { this.attendanceScore = attendanceScore; }

    public Double getBehaviorScore() { return behaviorScore; }
    public void setBehaviorScore(Double behaviorScore) { this.behaviorScore = behaviorScore; }

    public Double getInitiativeScore() { return initiativeScore; }
    public void setInitiativeScore(Double initiativeScore) { this.initiativeScore = initiativeScore; }

    public Double getLearningScore() { return learningScore; }
    public void setLearningScore(Double learningScore) { this.learningScore = learningScore; }

    public Double getTotalScore() { return totalScore; }
    public void setTotalScore(Double totalScore) { this.totalScore = totalScore; }

    public String getPerformanceRating() { return performanceRating; }
    public void setPerformanceRating(String performanceRating) { this.performanceRating = performanceRating; }

    public String getBehaviorRemarks() { return behaviorRemarks; }
    public void setBehaviorRemarks(String behaviorRemarks) { this.behaviorRemarks = behaviorRemarks; }

    public Integer getBehaviorRatingStars() { return behaviorRatingStars; }
    public void setBehaviorRatingStars(Integer behaviorRatingStars) { this.behaviorRatingStars = behaviorRatingStars; }

    public Integer getPresentDays() { return presentDays; }
    public void setPresentDays(Integer presentDays) { this.presentDays = presentDays; }

    public Integer getAbsentDays() { return absentDays; }
    public void setAbsentDays(Integer absentDays) { this.absentDays = absentDays; }

    public Integer getLateDays() { return lateDays; }
    public void setLateDays(Integer lateDays) { this.lateDays = lateDays; }

    public Integer getTotalWorkingDays() { return totalWorkingDays; }
    public void setTotalWorkingDays(Integer totalWorkingDays) { this.totalWorkingDays = totalWorkingDays; }

    public Double getAttendancePercentage() { return attendancePercentage; }
    public void setAttendancePercentage(Double attendancePercentage) { this.attendancePercentage = attendancePercentage; }

    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public LocalDateTime getReviewedDate() { return reviewedDate; }
    public void setReviewedDate(LocalDateTime reviewedDate) { this.reviewedDate = reviewedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
