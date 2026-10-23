package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_risks", indexes = {
        @Index(name = "idx_pm_risk_project", columnList = "project_id"),
        @Index(name = "idx_pm_risk_status",  columnList = "status"),
        @Index(name = "idx_pm_risk_score",   columnList = "score")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Risk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "likelihood", nullable = false, length = 20)
    private String likelihood;   // LOW | MEDIUM | HIGH

    @Column(name = "impact", nullable = false, length = 20)
    private String impact;       // LOW | MEDIUM | HIGH | CRITICAL

    /** score = likelihoodWeight × impactWeight (1..25). */
    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "status", nullable = false, length = 20)
    private String status;       // OPEN | MITIGATING | RESOLVED | ACCEPTED

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "mitigation", columnDefinition = "TEXT")
    private String mitigation;

    @Column(name = "review_date")
    private LocalDate reviewDate;

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
        if (status == null) status = "OPEN";
        if (likelihood == null) likelihood = "MEDIUM";
        if (impact == null) impact = "MEDIUM";
        if (score == null) score = computeScore(likelihood, impact);
    }

    public static int computeScore(String likelihood, String impact) {
        int l = switch (likelihood == null ? "MEDIUM" : likelihood.toUpperCase()) {
            case "HIGH" -> 5;
            case "MEDIUM" -> 3;
            default -> 1;
        };
        int i = switch (impact == null ? "MEDIUM" : impact.toUpperCase()) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            default -> 1;
        };
        return l * i;
    }
}