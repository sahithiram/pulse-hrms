package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** One retrospective per sprint (unique sprint_id). Created lazily with the first item. */
@Entity
@Table(name = "pm_sprint_retro", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pm_retro_sprint", columnNames = "sprint_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SprintRetro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sprint_id", nullable = false)
    private Long sprintId;

    @Column(name = "created_by")
    private Long createdBy;

    /** non-null = closed: read-only history until a manager reopens it */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}