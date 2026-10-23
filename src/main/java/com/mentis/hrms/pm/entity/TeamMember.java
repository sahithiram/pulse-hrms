package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_team_members", indexes = {
        @Index(name = "idx_pm_tm_team", columnList = "team_id"),
        @Index(name = "idx_pm_tm_emp", columnList = "employee_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "role_in_team", length = 80)
    private String roleInTeam;  // Team Lead, Developer, QA, etc.

    @Column(name = "allocation_pct")
    private Integer allocationPct;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;
}