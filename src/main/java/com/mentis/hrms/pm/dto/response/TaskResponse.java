package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.enums.TaskType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long parentTaskId;

    private Long teamId;
    private String teamName;

    // ─── Phase 5 additions ─────────────────────────────────────
    private Long sprintId;
    private String sprintName;
    private Long milestoneId;
    private String milestoneName;
    private Long keyResultId;

    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private TaskType type;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimateHours;
    private Integer loggedHours;
    private Integer loggedMinutes;
    private Integer storyPoints;
    private Integer progressPct;

    private List<Long> assigneeIds;
    private List<String> assigneeNames;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private long subtaskCount;
    private long subtaskDone;
    private long openBlockerCount;

    // ─── Phase 6 addition ─────────────────────────────────────
    /** Human-readable capacity warnings from WorkloadService when assigning. */
    private List<String> assignmentWarnings;
}