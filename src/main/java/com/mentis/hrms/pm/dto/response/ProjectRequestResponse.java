package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectRequestResponse {
    private Long id;
    private String title;
    private String problem;
    private String expectedOutcome;
    private Integer impact, urgency, effort;
    private Integer priorityScore;
    private String suggestedPriority;
    private LocalDate desiredStartDate;
    private LocalDate desiredEndDate;
    private String status;
    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private Long decidedBy;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String note;
    private Long projectId;
    private Long templateId;
}