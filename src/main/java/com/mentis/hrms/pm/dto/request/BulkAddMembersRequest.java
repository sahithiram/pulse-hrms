package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Add several people to a project at once; role / allocation / rate apply to every person. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BulkAddMembersRequest {
    @NotEmpty @Size(max = 200) private List<Long> employeeIds;
    private ProjectRole role;                              // default MEMBER
    @Min(0) @Max(100) private Integer allocationPct;       // default 100
    @DecimalMin("0.0") private BigDecimal hourlyRate;
    private LocalDate startDate;
    private LocalDate endDate;
}