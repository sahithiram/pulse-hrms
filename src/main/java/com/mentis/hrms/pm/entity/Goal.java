package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.GoalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_goals", indexes = {
        @Index(name = "idx_pm_goal_project", columnList = "project_id"),
        @Index(name = "idx_pm_goal_owner",   columnList = "owner_id"),
        @Index(name = "idx_pm_goal_status",  columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id")
    private Long projectId;              // nullable — goals can be company-wide

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "quarter", length = 10)
    private String quarter;              // Q1 | Q2 | Q3 | Q4

    @Column(name = "year")
    private Integer year;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GoalStatus status;

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

    @PrePersist
    void prePersist() {
        if (status == null) status = GoalStatus.ACTIVE;
        if (progressPct == null) progressPct = 0;
    }
}