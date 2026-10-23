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
@Table(name = "pm_tasks", indexes = {
        @Index(name = "idx_pm_task_project", columnList = "project_id"),
        @Index(name = "idx_pm_task_status",  columnList = "status"),
        @Index(name = "idx_pm_task_parent",  columnList = "parent_task_id"),
        @Index(name = "idx_pm_task_team",    columnList = "team_id")
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

    // NEW: optional team ownership — filters assignees, drives team dashboard
    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
    // ─── PHASE 5 additions ─────────────────────────────────────
    /** Sprint this task belongs to. NULL = backlog. */
    @Column(name = "sprint_id")
    private Long sprintId;

    /** Milestone this task belongs to. NULL = no milestone. */
    @Column(name = "milestone_id")
    private Long milestoneId;

    /** Key result this task contributes to. NULL = no KR link. */
    @Column(name = "key_result_id")
    private Long keyResultId;
    @PrePersist
    void prePersist() {
        if (progressPct == null) progressPct = 0;
        if (status == null) status = TaskStatus.TODO;
        if (priority == null) priority = TaskPriority.MEDIUM;
        if (type == null) type = TaskType.TASK;
        if (loggedHours == null) loggedHours = 0;
        if (loggedMinutes == null) loggedMinutes = 0;
    }
}