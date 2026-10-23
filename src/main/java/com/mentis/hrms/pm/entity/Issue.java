package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_issues", indexes = {
        @Index(name = "idx_pm_issue_project", columnList = "project_id"),
        @Index(name = "idx_pm_issue_task",    columnList = "linked_task_id"),
        @Index(name = "idx_pm_issue_status",  columnList = "status"),
        @Index(name = "idx_pm_issue_sev",     columnList = "severity")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "linked_task_id")
    private Long linkedTaskId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "severity", nullable = false, length = 20)
    private String severity;   // LOW | MEDIUM | HIGH | CRITICAL

    @Column(name = "priority", nullable = false, length = 20)
    private String priority;   // LOW | MEDIUM | HIGH | CRITICAL

    @Column(name = "status", nullable = false, length = 20)
    private String status;     // OPEN | IN_PROGRESS | RESOLVED | CLOSED | WONT_FIX

    @Column(name = "type", nullable = false, length = 30)
    private String type;       // BUG | FEATURE | SECURITY | PERFORMANCE | QUESTION

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "reported_by")
    private Long reportedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "OPEN";
        if (severity == null) severity = "MEDIUM";
        if (priority == null) priority = "MEDIUM";
        if (type == null) type = "BUG";
    }
}