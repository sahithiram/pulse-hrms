package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_teams", indexes = {
        @Index(name = "idx_pm_team_name", columnList = "name"),
        @Index(name = "idx_pm_team_lead", columnList = "lead_employee_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "icon", length = 50)
    private String icon;   // font-awesome class, e.g. "fa-server"

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "lead_employee_id")
    private Long leadEmployeeId;

    @Column(name = "status", length = 30)
    private String status; // on-track, at-risk, planning

    @Column(name = "weekly_hours")
    private Integer weeklyHours;

    @Column(name = "timezone", length = 60)
    private String timezone;

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
        if (status == null) status = "on-track";
        if (icon == null) icon = "fa-users";
        if (weeklyHours == null) weeklyHours = 40;
    }
}