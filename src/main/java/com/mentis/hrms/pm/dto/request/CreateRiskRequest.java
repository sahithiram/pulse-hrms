package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateRiskRequest {
    @NotNull private Long projectId;
    @NotBlank private String title;
    private String description;
    private String likelihood;   // LOW | MEDIUM | HIGH
    private String impact;       // LOW | MEDIUM | HIGH | CRITICAL
    private Long ownerId;
    private String mitigation;
    private LocalDate reviewDate;
}