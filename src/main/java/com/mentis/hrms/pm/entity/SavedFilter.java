package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_saved_filters")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavedFilter {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;
    @Column(name = "is_default", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private boolean isDefault;
    /** null = usable in any project */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** JSON of TaskFilterRequest */
    @Column(name = "criteria", nullable = false, columnDefinition = "TEXT")
    private String criteria;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}