package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Every field is optional: only the ones that are sent get changed. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateTimeLogRequest {

    @Min(1) @Max(960)
    private Integer minutes;

    private Boolean billable;

    @Size(max = 500)
    private String note;
}