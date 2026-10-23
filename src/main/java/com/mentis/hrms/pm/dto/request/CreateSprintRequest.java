package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateSprintRequest {
    @NotBlank private String name;
    private String goal;
    @NotNull  private LocalDate startDate;
    @NotNull  private LocalDate endDate;
    private Integer velocity;
}