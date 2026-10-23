package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateGoalRequest {
    @NotBlank private String title;
    private String description;
    private Long projectId;
    private Long ownerId;
    private String quarter;
    private Integer year;
}