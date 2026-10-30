package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.TaskPriority;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_recurring_task_rule")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RecurringTaskRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 300) private String title;
    @Column(columnDefinition = "TEXT") private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private TaskPriority priority;

    @Column(name = "assignee_id") private Long assigneeId;

    @Column(nullable = false, length = 10) private String frequency;
    @Column(name = "day_of_week") private Integer dayOfWeek;
    @Column(name = "day_of_month") private Integer dayOfMonth;
    @Column(name = "due_after_days", nullable = false) private int dueAfterDays;

    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "next_run_date", nullable = false) private LocalDate nextRunDate;

    @Column(nullable = false) private boolean paused;
    @Column(name = "created_by") private Long createdBy;

    @CreationTimestamp @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}