package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_key_results", indexes = {
        @Index(name = "idx_pm_kr_goal", columnList = "goal_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KeyResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "metric", nullable = false, length = 300)
    private String metric;               // "Complete 6 migration milestones"

    @Column(name = "unit", length = 30)
    private String unit;                 // "%", "count", "days", "usd", "boolean"

    @Column(name = "target_value")
    private Double targetValue;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "baseline_value")
    private Double baselineValue;

    @Column(name = "progress_pct", nullable = false)
    private Integer progressPct;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (progressPct == null) progressPct = 0;
        if (currentValue == null) currentValue = 0.0;
    }
}