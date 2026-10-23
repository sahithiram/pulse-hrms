package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.SprintStatus;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SprintResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
    private SprintStatus status;
    private Integer velocity;
    private String retroNotes;

    private long taskCount;
    private long doneCount;
    private long committedPoints;
    private long completedPoints;
    private long daysRemaining;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}