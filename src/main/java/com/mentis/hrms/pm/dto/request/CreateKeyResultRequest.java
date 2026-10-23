package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateKeyResultRequest {
    @NotBlank private String metric;
    private String unit;
    private Double targetValue;
    private Double currentValue;
    private Double baselineValue;
}