package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_project_closure_items", indexes = {
        @Index(name = "idx_pm_closure_project", columnList = "project_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectClosureItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "done", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean done;

    @Column(name = "done_by")
    private Long doneBy;

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}