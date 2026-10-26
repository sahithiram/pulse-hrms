package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectHealth;
import com.mentis.hrms.pm.enums.ProjectPriority;
import com.mentis.hrms.pm.enums.ProjectStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProjectRequest {
    private String name;
    private String description;
    private Long ownerId;
    private Long departmentId;
    private Long clientId;
    private ProjectStatus status;
    private ProjectHealth health;
    private ProjectPriority priority;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budgetTotal;
    private Integer progressPct;
    private Long teamId;
    private Boolean reviewRequired;
    private String lessonsLearned;
}