package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/** Add several people to a team at once; role / allocation apply to every person. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkAddTeamMembersRequest {
    @NotEmpty @Size(max = 200) private List<Long> employeeIds;
    @Size(max = 80) private String roleInTeam;
    @Min(0) @Max(100) private Integer allocationPct;
}