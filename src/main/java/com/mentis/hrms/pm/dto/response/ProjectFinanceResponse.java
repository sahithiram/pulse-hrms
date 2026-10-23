package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Everything the "Health & budget" panel needs, in one call (manager-only: it contains money). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectFinanceResponse {
    private Long projectId;
    private String projectName;
    private String status;

    private BigDecimal budgetTotal;
    private BigDecimal actualCost;            // approved timesheets × hourly rate
    private BigDecimal pendingCost;           // submitted / draft hours × rate (not yet approved)
    private BigDecimal remaining;             // budget − actual
    private BigDecimal forecastAtCompletion;  // actual ÷ progress; null while progress < 10%

    private double burnPct;
    private int progressPct;
    private double elapsedPct;
    private double slipPct;                   // elapsed − progress (positive = behind)

    private long openTasks;
    private long overdueTasks;
    private long blockedTasks;
    private long criticalIssues;
    private long highRisks;

    private Baseline baseline;                // null until a baseline is saved
    private Health health;
    private List<CostLine> lines;
    private List<String> warnings;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Baseline {
        private LocalDate start;
        private LocalDate end;
        private BigDecimal budget;
        private LocalDateTime at;
        private String byName;
        private LocalDate currentStart;
        private LocalDate currentEnd;
        private BigDecimal currentBudget;
        private Long startVarianceDays;       // current − baseline (positive = later)
        private Long endVarianceDays;
        private BigDecimal budgetVariance;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Health {
        private String value;                 // what the system currently shows
        private String computed;              // what the rules say right now
        private String mode;                  // AUTO | MANUAL
        private boolean tracked;              // false for planning / on hold / completed …
        private List<String> reasons;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CostLine {
        private Long employeeId;
        private String name;
        private BigDecimal rate;
        private boolean projectMember;
        private boolean rateMissing;
        private int approvedMinutes;
        private int pendingMinutes;
        private BigDecimal approvedCost;
        private BigDecimal pendingCost;
    }
}