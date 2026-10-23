package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkloadResponse {
    private Long employeePk;
    private String employeeId;
    private String employeeName;
    private String department;
    private Long teamId;
    private String teamName;

    private LocalDate from;
    private LocalDate to;

    private double capacityHours;
    private double leaveHours;
    private double holidayHours;
    private double allocatedHours;
    private double loggedHours;
    private int    workingDays;
    private int    leaveDays;

    private int    utilizationPct;
    private String status;
    private List<LeaveBreakdown> leaves;
    private List<TaskBreakdown>  tasks;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LeaveBreakdown {
        private String leaveId;
        private String leaveType;
        private LocalDate startDate;
        private LocalDate endDate;
        private double days;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskBreakdown {
        private Long taskId;
        private String title;
        private String status;
        private String priority;
        private Integer estimateHours;
        private LocalDate startDate;
        private LocalDate dueDate;
    }
}