package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.enums.TaskType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uq_pm_task_recurring", columnNames = {"recurring_rule_id", "recurring_occurrence"}),
        indexes = {
                @Index(name = "idx_pm_task_project", columnList = "project_id"),
                @Index(name = "idx_pm_task_status", columnList = "status"),
                @Index(name = "idx_pm_task_parent", columnList = "parent_task_id"),
                @Index(name = "idx_pm_task_team", columnList = "team_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parent_task_id")
    private Long parentTaskId;
    @Column(name = "recurring_rule_id")
    private Long recurringRuleId;

    @Column(name = "recurring_occurrence")
    private java.time.LocalDate recurringOccurrence;
    // optional team ownership — filters assignees, drives team dashboard
    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // ─── Baseline (planned) values ───────────────────────────────────────────
    // Set once at baseline time and never mutated afterwards. Used by the
    // planned-vs-actual / schedule-slip report.
    @Column(name = "baseline_start")
    private LocalDate baselineStart;

    @Column(name = "baseline_due")
    private LocalDate baselineDue;

    @Column(name = "baseline_estimate_hours")
    private Integer baselineEstimateHours;

    // ─── Actuals ─────────────────────────────────────────────────────────────
    // Stamped/cleared by applyCompletionTransition(...) whenever the task
    // transitions into or out of DONE. Both updateTask/changeStatus (via
    // trackBlocked) and the sign-off approval path must call it so the slip
    // report has a consistent completion timestamp.
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TaskType type;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "estimate_hours")
    private Integer estimateHours;

    @Column(name = "logged_hours")
    private Integer loggedHours;

    /** exact minutes logged (loggedHours is only a rounded display value) */
    @Column(name = "logged_minutes")
    private Integer loggedMinutes;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Column(name = "progress_pct", nullable = false)
    private Integer progressPct;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── PHASE 5 additions ────────────────────────────────────────────────
    /** Sprint this task belongs to. NULL = backlog. */
    @Column(name = "sprint_id")
    private Long sprintId;

    /** Milestone this task belongs to. NULL = no milestone. */
    @Column(name = "milestone_id")
    private Long milestoneId;

    /** Key result this task contributes to. NULL = no KR link. */
    @Column(name = "key_result_id")
    private Long keyResultId;

    // ─── Step 5 ───────────────────────────────────────────────────────────
    /** When the task last became BLOCKED. NULL when not blocked. */
    @Column(name = "blocked_since")
    private LocalDateTime blockedSince;

    /** 0 = none sent, 1 = "still blocked" sent, 2 = escalation sent. Reset when unblocked. */
    @Column(name = "escalation_level", nullable = false, columnDefinition = "INT NOT NULL DEFAULT 0")
    private Integer escalationLevel;

    /**
     * Applies the completion side-effects of a status transition.
     *
     * Centralised so that EVERY path that moves a task into or out of DONE
     * (updateTask, changeStatus, sign-off approval, future automation) stamps
     * or clears completedAt identically. Otherwise the slip report would have
     * to fall back to dueDate for some tasks and report zero slip.
     *
     * Safe to call with {@code from == null} (create path).
     */
    public void applyCompletionTransition(TaskStatus from, TaskStatus to) {
        if (to == TaskStatus.DONE && from != TaskStatus.DONE) {
            this.completedAt = LocalDateTime.now();
        } else if (from == TaskStatus.DONE && to != TaskStatus.DONE) {
            this.completedAt = null;
        }
    }

    @PrePersist
    void prePersist() {
        if (progressPct == null) progressPct = 0;
        if (status == null) status = TaskStatus.TODO;
        if (priority == null) priority = TaskPriority.MEDIUM;
        if (type == null) type = TaskType.TASK;
        if (loggedHours == null) loggedHours = 0;
        if (loggedMinutes == null) loggedMinutes = 0;
        if (escalationLevel == null) escalationLevel = 0;
    }
}