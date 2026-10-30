package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity

@Table(name = "pm_task_time_logs", indexes = {
        @Index(name = "idx_pm_tl_task", columnList = "task_id"),
        @Index(name = "idx_pm_tl_emp_date", columnList = "employee_id, log_date"),

})

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskTimeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "log_date", nullable = false)
    private LocalDate logDate;

    @Column(name = "minutes", nullable = false)
    private Integer minutes;

    @Column(name = "note", length = 500)
    private String note;
    @Builder.Default
    @Column(name = "billable", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private Boolean billable = true;

    @Builder.Default
    @Column(name = "overtime", nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 0")
    private Boolean overtime = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}