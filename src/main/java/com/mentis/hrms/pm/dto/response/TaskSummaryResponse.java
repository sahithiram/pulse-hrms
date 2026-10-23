package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskStatus;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskSummaryResponse {
    private Long id;
    private String title;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
    private Integer progressPct;
    private Integer storyPoints;

    private Long projectId;
    private String projectName;

    private Long teamId;
    private String teamName;

    private Long parentTaskId;         // NEW
    private List<Long> assigneeIds;
    private List<String> assigneeNames;

    private long subtaskCount;         // NEW — card badge
    private long subtaskDone;          // NEW
    private long openBlockerCount;
    private Long sprintId;
    private String sprintName;
    private Long milestoneId;
    private String milestoneName;// NEW — red badge
}