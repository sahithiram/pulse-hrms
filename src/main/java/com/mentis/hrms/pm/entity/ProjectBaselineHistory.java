package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A baseline that was replaced by an approved change request. */
@Entity
@Table(name = "pm_project_baselines", indexes = {
        @Index(name = "idx_pm_baseline_project", columnList = "project_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectBaselineHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** 1 = the first baseline that was ever superseded. */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

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

    @Column(name = "superseded_at", nullable = false)
    private LocalDateTime supersededAt;

    /** pm_approval_requests.id of the change request that replaced it. */
    @Column(name = "change_request_id")
    private Long changeRequestId;

    @Column(name = "reason", length = 1000)
    private String reason;
}