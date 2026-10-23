package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.TimesheetStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_timesheet_weeks",
        uniqueConstraints = @UniqueConstraint(name = "uq_pm_ts_week",
                columnNames = {"employee_id", "week_start"}),
        indexes = {@Index(name = "idx_pm_ts_status", columnList = "status")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimesheetWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;                       // always a Monday

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TimesheetStatus status;

    @Column(name = "total_minutes")
    private Integer totalMinutes;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "approval_id")
    private Long approvalId;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "note", length = 500)
    private String note;                               // approver's note / reopen reason

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = TimesheetStatus.DRAFT;
    }
}