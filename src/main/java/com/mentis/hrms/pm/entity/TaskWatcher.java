package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_task_watchers", indexes = {
        @Index(name = "idx_pm_wt_task", columnList = "task_id"),
        @Index(name = "idx_pm_wt_emp", columnList = "employee_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskWatcher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}