package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.SprintStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_sprints", indexes = {
        @Index(name = "idx_pm_sprint_project", columnList = "project_id"),
        @Index(name = "idx_pm_sprint_status",  columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Sprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "goal", columnDefinition = "TEXT")
    private String goal;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SprintStatus status;

    @Column(name = "velocity")
    private Integer velocity;

    /** story points committed at the moment the sprint was closed (snapshot) */
    @Column(name = "committed_points")
    private Integer committedPoints;

    @Column(name = "retro_notes", columnDefinition = "TEXT")
    private String retroNotes;

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
        if (status == null) status = SprintStatus.PLANNED;
    }
}