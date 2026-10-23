package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateCommentRequest {

    @NotBlank
    private String body;

    private String mentions;  // comma-separated employee ids
}