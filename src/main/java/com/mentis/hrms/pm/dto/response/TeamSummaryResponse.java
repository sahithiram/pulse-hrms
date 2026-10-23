package com.mentis.hrms.pm.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamSummaryResponse {
    private Long id;
    private String name;
    private String icon;
    private String department;
    private String status;
    private Long memberCount;
    private Long leadEmployeeId;
    private String leadName;    // NEW — display-friendly
    private Integer weeklyHours; // NEW — for workload math
}