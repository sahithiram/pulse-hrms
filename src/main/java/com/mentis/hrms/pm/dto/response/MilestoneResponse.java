package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.MilestoneStatus;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MilestoneResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private String name;
    private String description;
    private LocalDate dueDate;
    private MilestoneStatus status;
    private Long ownerId;
    private String ownerName;
    private Integer progressPct;
    private long taskCount;
    private long doneCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}