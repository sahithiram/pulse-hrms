package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlanActualResponse {
    private Long projectId;
    private boolean baselined;
    private LocalDateTime baselinedAt;
    private int baselineVersion;
    private boolean locked;
    private boolean canManage;
    private Summary summary;
    private List<Row> rows;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Summary {
        private Integer onTimePct;          // null when no finished task has a baseline
        private Double avgSlipDays;         // null when nothing to measure
        private Double hoursVariancePct;    // null when no baseline hours
        private int lateTasks;
        private int addedAfterBaseline;
    }

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Row {
        private Long taskId;
        private String title;
        private String status;
        private LocalDate baselineDue;
        private LocalDate currentDue;
        private LocalDate completedOn;
        private Integer slipDays;           // signed; the UI shows max(0, slip)
        private Double baselineHours;
        private Double loggedHours;
        private boolean addedAfterBaseline;
    }
}