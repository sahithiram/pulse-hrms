package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateTeamMemberRequest {
    @Size(max = 80) private String roleInTeam;
    @Min(0) @Max(100) private Integer allocationPct;
}