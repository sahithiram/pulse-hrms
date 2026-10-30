package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class VarianceReport {
    private Long projectId;
    private double baselineHours;
    private double plannedHours;
    private double actualHours;
    private double hoursVariance;
    private double billableHours;
    private double nonBillableHours;
    private double overtimeHours;
    private int tasksWithoutBaseline;
    private int lateTasks;
    private List<Row> rows;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Row {
        private Long taskId;
        private String title;
        private String status;
        private Double baselineHours;
        private Double plannedHours;
        private Double actualHours;
        private Double hoursVariance;
        private LocalDate baselineStart;
        private LocalDate baselineDue;
        private LocalDate dueDate;
        private LocalDate completedDate;
        private Integer slipDays;
        private boolean addedAfterBaseline;
    }
}