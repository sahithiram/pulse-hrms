package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDate;

/** One row of the read-only retro history of a project. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RetroSummaryResponse {
    private Long sprintId;
    private String sprintName;
    private String sprintStatus;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean hasRetro;
    private boolean closed;
    private int wentWell;
    private int toImprove;
    private int actions;
    private int converted;
}