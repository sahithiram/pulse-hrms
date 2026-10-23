package com.mentis.hrms.pm.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TeamMemberResponse {
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private String roleInTeam;
    private Integer allocationPct;
    private boolean lead;
}