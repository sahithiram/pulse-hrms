package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

public final class ReportDtos {
    private ReportDtos() {}

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BudgetRow {
        private Long projectId;
        private String projectName;
        private Double budgetTotal;
        private Double budgetSpent;
        private Double remaining;
        private Double spentPct;
        private String status;   // OK | WARN | OVER
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VelocityRow {
        private Long sprintId;
        private String sprintName;
        private LocalDate startDate;
        private LocalDate endDate;
        private long committedPoints;
        private long completedPoints;
        private Double completionPct;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QualityReport {
        private long openIssues;
        private long criticalIssues;
        private long resolvedLast30d;
        private Double avgResolutionDays;
        private List<SeverityCount> bySeverity;
        private List<TypeCount> byType;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SeverityCount {
        private String severity;
        private long count;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TypeCount {
        private String type;
        private long count;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RiskReport {
        private long totalRisks;
        private long highSeverity;
        private long open;
        private long mitigating;
        private List<SeverityCount> bySeverity;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UtilizationRow {
        private Long employeePk;
        private String employeeId;
        private String employeeName;
        private String department;
        private double capacityHours;
        private double allocatedHours;
        private double loggedHours;
        private int utilizationPct;
        private String status;
    }
}