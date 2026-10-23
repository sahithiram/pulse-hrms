package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.ProjectHealth;
import com.mentis.hrms.pm.enums.ProjectPriority;
import com.mentis.hrms.pm.enums.ProjectStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Long ownerId;
    private Long departmentId;
    private Long clientId;
    private String clientName;
    private ProjectStatus status;
    private ProjectHealth health;
    private ProjectPriority priority;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budgetTotal;
    private BigDecimal budgetSpent;
    private Integer progressPct;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long teamId;

    // Phase 5
    private LocalDate baselineStart;
    private LocalDate baselineEnd;
    private BigDecimal baselineBudget;
    private LocalDateTime baselinedAt;
    private String healthMode;          // AUTO | MANUAL
    private String healthReason;
    private LocalDateTime healthUpdatedAt;
    private String lessonsLearned;
}