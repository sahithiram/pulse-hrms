package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_active_timer")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ActiveTimer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false) private Long employeeId;
    @Column(name = "task_id", nullable = false) private Long taskId;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;   // Asia/Kolkata wall time
    @Column(length = 500) private String note;
    @Column(nullable = false) private boolean billable;
}