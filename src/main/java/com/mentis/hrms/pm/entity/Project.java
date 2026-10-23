package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.ProjectHealth;
import com.mentis.hrms.pm.enums.ProjectPriority;
import com.mentis.hrms.pm.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.mentis.hrms.pm.enums.HealthMode;
@Entity
@Table(name = "pm_projects", indexes = {
        @Index(name = "idx_pm_project_code", columnList = "code", unique = true),
        @Index(name = "idx_pm_project_status", columnList = "status"),
        @Index(name = "idx_pm_project_owner", columnList = "owner_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // FK to existing employees table (do not recreate)
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    // FK to existing departments table
    @Column(name = "department_id")
    private Long departmentId;

    // Client is a PM-only table, created later
    @Column(name = "client_id")
    private Long clientId;
    // ↓ ADD THIS
    @Column(name = "team_id")
    private Long teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "health", length = 20)
    private ProjectHealth health;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private ProjectPriority priority;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "budget_total", precision = 14, scale = 2)
    private BigDecimal budgetTotal;

    @Column(name = "budget_spent", precision = 14, scale = 2)
    private BigDecimal budgetSpent;

    // ── Phase 5: baseline (snapshot of the plan) ──
    @Column(name = "baseline_start")
    private LocalDate baselineStart;

    @Column(name = "baseline_end")
    private LocalDate baselineEnd;

    @Column(name = "baseline_budget", precision = 14, scale = 2)
    private BigDecimal baselineBudget;

    @Column(name = "baselined_at")
    private LocalDateTime baselinedAt;

    @Column(name = "baselined_by")
    private Long baselinedBy;

    // ── Phase 5: health bookkeeping ──
    @Enumerated(EnumType.STRING)
    @Column(name = "health_mode", length = 10)
    private HealthMode healthMode;              // null = AUTO (older rows)

    @Column(name = "health_reason", length = 500)
    private String healthReason;                // last automatic explanation

    @Column(name = "health_updated_at")
    private LocalDateTime healthUpdatedAt;

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
    // ── Phase 6.2: closure ──
    @Column(name = "lessons_learned", columnDefinition = "TEXT")
    private String lessonsLearned;

    @PrePersist
    void prePersist() {
        if (progressPct == null) progressPct = 0;
        if (budgetSpent == null) budgetSpent = BigDecimal.ZERO;
        if (healthMode == null) healthMode = HealthMode.AUTO;

        if (status == null) status = ProjectStatus.PLANNING;
        if (priority == null) priority = ProjectPriority.MEDIUM;
    }
}