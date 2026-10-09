package com.mentis.hrms.workperformance.dto;

/**
 * NEW file. One row of the combined performance view — merges the existing
 * 3-pillar AdminEmpPerformance data with the new ClickUp work score
 * OR the PM work score (Phase 6 fallback).
 *
 * When hasClickUpMapping is false, workScore is populated from PM tasks
 * (if any exist), otherwise null and finalScore is the Attendance/Behavior/
 * Initiative weights reweighted to fill 100%.
 */
public class CombinedPerformanceDTO {

    private String employeeId;
    private String employeeName;
    private String department;

    private Double attendanceScore;
    private Double behaviorScore;
    private Double initiativeScore;
    private Double workScore;          // null if not applicable

    private Double finalScore;         // always populated
    private String finalRating;

    private boolean hasClickUpMapping;
    private boolean needsSync;         // mapped, but no sync run yet this month

    private Integer totalTasksAssigned;
    private Integer completedTasks;

    // ===== PHASE 6 additions =====
    /** "CLICKUP" | "PM" | "NONE" — which work-pillar source was used. */
    private String workScoreSource;

    /** Raw PM stats for transparency when workScoreSource = "PM". */
    private Integer pmTasksAssigned;
    private Integer pmTasksCompleted;
    private Integer pmTasksOnTime;

    // ===== Getters & Setters =====
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public Double getAttendanceScore() { return attendanceScore; }
    public void setAttendanceScore(Double attendanceScore) { this.attendanceScore = attendanceScore; }

    public Double getBehaviorScore() { return behaviorScore; }
    public void setBehaviorScore(Double behaviorScore) { this.behaviorScore = behaviorScore; }

    public Double getInitiativeScore() { return initiativeScore; }
    public void setInitiativeScore(Double initiativeScore) { this.initiativeScore = initiativeScore; }

    public Double getWorkScore() { return workScore; }
    public void setWorkScore(Double workScore) { this.workScore = workScore; }

    public Double getFinalScore() { return finalScore; }
    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }

    public String getFinalRating() { return finalRating; }
    public void setFinalRating(String finalRating) { this.finalRating = finalRating; }

    public boolean isHasClickUpMapping() { return hasClickUpMapping; }
    public void setHasClickUpMapping(boolean hasClickUpMapping) { this.hasClickUpMapping = hasClickUpMapping; }

    public boolean isNeedsSync() { return needsSync; }
    public void setNeedsSync(boolean needsSync) { this.needsSync = needsSync; }

    public Integer getTotalTasksAssigned() { return totalTasksAssigned; }
    public void setTotalTasksAssigned(Integer totalTasksAssigned) { this.totalTasksAssigned = totalTasksAssigned; }

    public Integer getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(Integer completedTasks) { this.completedTasks = completedTasks; }

    public String getWorkScoreSource() { return workScoreSource; }
    public void setWorkScoreSource(String workScoreSource) { this.workScoreSource = workScoreSource; }

    public Integer getPmTasksAssigned() { return pmTasksAssigned; }
    public void setPmTasksAssigned(Integer pmTasksAssigned) { this.pmTasksAssigned = pmTasksAssigned; }

    public Integer getPmTasksCompleted() { return pmTasksCompleted; }
    public void setPmTasksCompleted(Integer pmTasksCompleted) { this.pmTasksCompleted = pmTasksCompleted; }

    public Integer getPmTasksOnTime() { return pmTasksOnTime; }
    public void setPmTasksOnTime(Integer pmTasksOnTime) { this.pmTasksOnTime = pmTasksOnTime; }
}
