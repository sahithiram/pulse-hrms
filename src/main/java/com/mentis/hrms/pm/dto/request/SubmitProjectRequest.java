package com.mentis.hrms.pm.dto.request;

import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SubmitProjectRequest {
    private String title;
    private String problem;
    private String expectedOutcome;
    private Integer impact;   // 1..5
    private Integer urgency;  // 1..5
    private Integer effort;   // 1..5 (5 = biggest)
    private LocalDate desiredStartDate; // optional
    private LocalDate desiredEndDate;   // optional
}