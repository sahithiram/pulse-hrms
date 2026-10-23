package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IssueResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long linkedTaskId;
    private String linkedTaskTitle;
    private String title;
    private String description;
    private String severity;
    private String priority;
    private String status;
    private String type;
    private Long assigneeId;
    private String assigneeName;
    private Long reportedBy;
    private String reportedByName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}