package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AddChecklistRequest {

    @NotBlank
    private String title;

    private Long parentId;
    private Long assigneeId;
}