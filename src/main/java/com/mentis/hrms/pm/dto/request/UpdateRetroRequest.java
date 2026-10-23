package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateRetroRequest {
    @Size(max = 10000) private String retroNotes;
}