package com.mentis.hrms.pm.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectTemplateResponse {
    private Long id;
    private String name;
    private String type;
    private String description;
    private int milestoneCount, sprintCount, taskCount;
    private JsonNode payload;
    private Long createdBy;
    private LocalDateTime createdAt;
}