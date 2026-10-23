package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_task_dependencies", indexes = {
        @Index(name = "idx_pm_dep_task", columnList = "task_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "depends_on_task_id", nullable = false)
    private Long dependsOnTaskId;

    @Column(name = "type", length = 20)
    private String type;  // BLOCKS, BLOCKED_BY, RELATES_TO, DUPLICATES

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}