package com.mentis.hrms.pm.dto.response;

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
public class ProjectSummaryResponse {
    private Long id;
    private String code;
    private String name;
    private ProjectStatus status;
    private ProjectHealth health;
    private ProjectPriority priority;
    private Integer progressPct;

    private Long clientId;
    private String clientName;
    private BigDecimal budgetTotal;
    private BigDecimal budgetSpent;
    private LocalDate startDate;
    private LocalDate endDate;

    // fields consumed by the frontend's mapBackendProject()
    private Long ownerId;
    private Long teamId;

    // STEP 1b: false when budget fields were hidden for this viewer
    private Boolean financeVisible;
}