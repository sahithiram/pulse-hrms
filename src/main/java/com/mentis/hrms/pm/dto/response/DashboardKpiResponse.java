package com.mentis.hrms.pm.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DashboardKpiResponse {
    private long activeProjects;
    private long completedProjects;
    private long atRiskProjects;
    private long openTasks;
    private long completedTasks;
    private long totalProjects;
    private long totalTasks;
}