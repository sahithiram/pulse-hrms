package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.enums.TaskType;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UpdateTaskRequest {
    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private TaskType type;
    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimateHours;
    private Integer storyPoints;
    private Integer progressPct;
    private Long teamId;
    private Long sprintId;
    private Long milestoneId;
    private Long keyResultId;

    // ─── Step 3.2C additions ──────────────────────────────────
    private Boolean clearStartDate;
    private Boolean clearDueDate;
}