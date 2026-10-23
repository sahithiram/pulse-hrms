package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateTaskRequest {

    @NotNull
    private Long projectId;

    private Long parentTaskId;

    private Long teamId;    // NEW — optional
    private Long sprintId;
    private Long milestoneId;
    private Long keyResultId;
    @NotBlank
    @Size(max = 300)
    private String title;

    private String description;

    @NotNull
    private TaskPriority priority;

    @NotNull
    private TaskType type;

    private LocalDate startDate;
    private LocalDate dueDate;
    private Integer estimateHours;
    private Integer storyPoints;
    private List<Long> assigneeIds;
}