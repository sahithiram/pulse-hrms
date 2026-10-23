package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateMilestoneRequest {
    @NotBlank private String name;
    private String description;
    private LocalDate dueDate;
    private Long ownerId;
}