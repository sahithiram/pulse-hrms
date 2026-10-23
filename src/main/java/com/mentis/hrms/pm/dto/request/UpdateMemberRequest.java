package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectRole;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateMemberRequest {
    private ProjectRole role;
    @Min(0) @Max(100) private Integer allocationPct;
    @DecimalMin("0.0") private BigDecimal hourlyRate;
    private LocalDate startDate;
    private LocalDate endDate;
}