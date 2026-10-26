package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.ProjectRequestStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** A project idea waiting for a decision. Becomes a project when approved. */
@Entity
@Table(name = "pm_project_requests", indexes = {
        @Index(name = "idx_pm_preq_status", columnList = "status"),
        @Index(name = "idx_pm_preq_creator", columnList = "created_by")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "problem", columnDefinition = "TEXT")
    private String problem;

    @Column(name = "expected_outcome", columnDefinition = "TEXT")
    private String expectedOutcome;

    @Column(name = "impact")
    private Integer impact;      // 1..5

    @Column(name = "urgency")
    private Integer urgency;     // 1..5

    @Column(name = "effort")
    private Integer effort;      // 1..5 (5 = biggest)

    @Column(name = "priority_score")
    private Integer priorityScore; // 20..100, computed

    /** STEP 2: dates the requester would like (optional, only a suggestion). */
    @Column(name = "desired_start_date")
    private LocalDate desiredStartDate;

    @Column(name = "desired_end_date")
    private LocalDate desiredEndDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectRequestStatus status;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "note", length = 1000)
    private String note;

    /** Set when approved: the project that was created. */
    @Column(name = "project_id")
    private Long projectId;

    /** Template used when approved (optional). */
    @Column(name = "template_id")
    private Long templateId;
}