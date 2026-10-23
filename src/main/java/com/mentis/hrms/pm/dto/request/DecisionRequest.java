package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DecisionRequest {
    /** APPROVED | REJECTED */
    @NotNull private String decision;
    private String note;
}