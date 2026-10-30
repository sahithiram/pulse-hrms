package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogTimeRequest {

    @NotNull
    private LocalDate logDate;

    @NotNull @Min(1)
    private Integer minutes;
    private Boolean billable;
    private String note;

}