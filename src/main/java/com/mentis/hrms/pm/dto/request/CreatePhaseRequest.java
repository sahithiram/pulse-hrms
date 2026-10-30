package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectPhaseStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreatePhaseRequest {
    @NotBlank private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectPhaseStatus status;
}
