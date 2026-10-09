package com.mentis.hrms.workperformance.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * NEW entity. One row per employee per review month.
 * Holds the raw ClickUp stats, the computed fair work score, and the
 * final combined score (Attendance + Behavior + Initiative + Work).
 *
 * IMPORTANT: This table is separate from admin_emp_performance.
 * The existing AdminEmpPerformance entity/table/screen is NOT modified —
 * we only READ from it (attendance/behavior/initiative) to compute the
 * combined total here.
 */
@Entity
@Table(name = "work_performance_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "review_month"}))
public class WorkPerformanceSnapshot {

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

    // ===== RAW CLICKUP STATS (for transparency / audit) =====
    @Column(name = "total_tasks_assigned")
    private Integer totalTasksAssigned;

    @Column(name = "completed_tasks")
    private Integer completedTasks;

    @Column(name = "on_time_completed")
    private Integer onTimeCompleted;

    // ===== WORK SCORE COMPONENTS (0-100 each) =====
    @Column(name = "completion_rate")
    private Double completionRate;

    @Column(name = "on_time_rate")
    private Double onTimeRate;

    @Column(name = "priority_score")
    private Double priorityScore;

    @Column(name = "efficiency_score")
    private Double efficiencyScore;

    @Column(name = "workload_normalized_score")
    private Double workloadNormalizedScore; // fairness component - see WorkPerformanceService

    @Column(name = "work_score")
    private Double workScore; // final ClickUp-derived score, 0-100

    // ===== SNAPSHOT OF THE OTHER 3 PILLARS AT SYNC TIME (read-only copy) =====
    @Column(name = "attendance_score_snapshot")
    private Double attendanceScoreSnapshot;

    @Column(name = "behavior_score_snapshot")
    private Double behaviorScoreSnapshot;

    @Column(name = "initiative_score_snapshot")
    private Double initiativeScoreSnapshot;

    // ===== FINAL COMBINED 4-PILLAR RESULT =====
    @Column(name = "final_combined_score")
    private Double finalCombinedScore;

    @Column(name = "final_rating", length = 30)
    private String finalRating;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    public WorkPerformanceSnapshot() {}

    // ===== Getters & Setters =====
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

    public Integer getTotalTasksAssigned() { return totalTasksAssigned; }
    public void setTotalTasksAssigned(Integer totalTasksAssigned) { this.totalTasksAssigned = totalTasksAssigned; }

    public Integer getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(Integer completedTasks) { this.completedTasks = completedTasks; }

    public Integer getOnTimeCompleted() { return onTimeCompleted; }
    public void setOnTimeCompleted(Integer onTimeCompleted) { this.onTimeCompleted = onTimeCompleted; }

    public Double getCompletionRate() { return completionRate; }
    public void setCompletionRate(Double completionRate) { this.completionRate = completionRate; }

    public Double getOnTimeRate() { return onTimeRate; }
    public void setOnTimeRate(Double onTimeRate) { this.onTimeRate = onTimeRate; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public Double getEfficiencyScore() { return efficiencyScore; }
    public void setEfficiencyScore(Double efficiencyScore) { this.efficiencyScore = efficiencyScore; }

    public Double getWorkloadNormalizedScore() { return workloadNormalizedScore; }
    public void setWorkloadNormalizedScore(Double workloadNormalizedScore) { this.workloadNormalizedScore = workloadNormalizedScore; }

    public Double getWorkScore() { return workScore; }
    public void setWorkScore(Double workScore) { this.workScore = workScore; }

    public Double getAttendanceScoreSnapshot() { return attendanceScoreSnapshot; }
    public void setAttendanceScoreSnapshot(Double attendanceScoreSnapshot) { this.attendanceScoreSnapshot = attendanceScoreSnapshot; }

    public Double getBehaviorScoreSnapshot() { return behaviorScoreSnapshot; }
    public void setBehaviorScoreSnapshot(Double behaviorScoreSnapshot) { this.behaviorScoreSnapshot = behaviorScoreSnapshot; }

    public Double getInitiativeScoreSnapshot() { return initiativeScoreSnapshot; }
    public void setInitiativeScoreSnapshot(Double initiativeScoreSnapshot) { this.initiativeScoreSnapshot = initiativeScoreSnapshot; }

    public Double getFinalCombinedScore() { return finalCombinedScore; }
    public void setFinalCombinedScore(Double finalCombinedScore) { this.finalCombinedScore = finalCombinedScore; }

    public String getFinalRating() { return finalRating; }
    public void setFinalRating(String finalRating) { this.finalRating = finalRating; }

    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
}
