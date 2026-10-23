package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateIssueRequest {
    @NotNull private Long projectId;
    private Long linkedTaskId;
    @NotBlank private String title;
    private String description;
    private String severity;    // LOW | MEDIUM | HIGH | CRITICAL
    private String priority;
    private String type;        // BUG | FEATURE | SECURITY | PERFORMANCE | QUESTION
    private Long assigneeId;
}