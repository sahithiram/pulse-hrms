package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Links a project to one of its teams. A project can have many teams. */
@Entity
@Table(name = "pm_project_teams",
        uniqueConstraints = @UniqueConstraint(name = "uq_pm_project_team", columnNames = {"project_id", "team_id"}),
        indexes = {@Index(name = "idx_pm_pteam_team", columnList = "team_id")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}