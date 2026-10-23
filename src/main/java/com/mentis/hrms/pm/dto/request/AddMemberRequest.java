package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AddMemberRequest {
    @NotNull private Long employeeId;
    private ProjectRole role;                 // default MEMBER
    @Min(0) @Max(100) private Integer allocationPct;   // default 100
    @DecimalMin("0.0") private BigDecimal hourlyRate;
    private LocalDate startDate;
    private LocalDate endDate;
}