package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.TemplateType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_project_templates", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pm_template_name", columnNames = "name")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TemplateType type;

    @Column(name = "description", length = 500)
    private String description;

    /** JSON text; shape = ProjectTemplatePayload. Validated on save. */
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(name = "created_by")
    private Long createdBy;   // null for seeded templates

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}