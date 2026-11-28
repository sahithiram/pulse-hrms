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
    private Long teamId;           // primary (first) team
    private java.util.List<Long> teamIds;

    // Phase 5
    private LocalDate baselineStart;
    private LocalDate baselineEnd;
    private BigDecimal baselineBudget;
    private LocalDateTime baselinedAt;
    private String healthMode; // AUTO | MANUAL
    private String healthReason;
    private LocalDateTime healthUpdatedAt;
    private String lessonsLearned;

    // STEP 1b: false when budget/baseline-budget were hidden for this viewer
    private Boolean financeVisible;
    private Boolean reviewRequired;

    // ── PM-025: multi-manager support (DisplayName strategy keeps the pipeline stable) ──
    /** Full name of the accountable owner (project.ownerId). */
    private String ownerName;
    /** Every employee with role MANAGER on this project (owner excluded). */
    private java.util.List<MemberRef> managers;
    /** Full name of the project LEAD, when there is one. */
    private String leadName;

    @lombok.Getter @lombok.Setter @lombok.NoArgsConstructor @lombok.AllArgsConstructor @lombok.Builder
    public static class MemberRef {
        private Long id;          // employee pk
        private String employeeId;
        private String name;
        private String initials;
        /** MANAGER | LEAD | MEMBER | VIEWER */
        private String role;
        private Integer allocationPct;
    }
}