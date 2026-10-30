package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.enums.TaskStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskFilterRequest {
    private Long projectId;
    private List<TaskStatus> statuses;
    private List<TaskPriority> priorities;
    private Long assigneeId;
    private boolean unassignedOnly;
    private LocalDate dueFrom;
    private LocalDate dueTo;
    private boolean overdueOnly;
    private String search;
    private int page = 0;
    private int size = 50;
    private Long sprintId;
    private Long milestoneId;
}