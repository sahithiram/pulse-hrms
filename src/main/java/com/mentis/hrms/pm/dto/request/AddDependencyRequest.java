package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AddDependencyRequest {

    @NotNull
    private Long dependsOnTaskId;

    private String type;   // BLOCKS, BLOCKED_BY, RELATES_TO
}