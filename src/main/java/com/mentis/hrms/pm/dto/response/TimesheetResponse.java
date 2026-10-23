package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimesheetResponse {
    private Long employeePk;
    private String employeeId;
    private String employeeName;
    private String department;
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private double totalHours;
    private int totalMinutes;
    private List<Day> days;

    // ── Phase 3: approval cycle ──
    private String status;            // DRAFT | SUBMITTED | APPROVED | REJECTED
    private boolean editable;         // true only for DRAFT / REJECTED
    private String statusNote;        // approver's note (REJECTED) or reopen reason
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private String decidedByName;
    private Long approverId;
    private String approverName;
    private Long approvalId;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Day {
        private LocalDate date;
        private String weekday;
        private double hours;
        private int minutes;
        private List<Entry> entries;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Entry {
        private Long timeLogId;
        private Long taskId;
        private String taskTitle;
        private Long projectId;
        private String projectName;
        private int minutes;
        private String note;
    }
}