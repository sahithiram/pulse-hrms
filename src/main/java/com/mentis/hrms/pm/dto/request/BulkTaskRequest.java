package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor
public class BulkTaskRequest {
    @NotEmpty @Size(max = 100)
    private List<Long> taskIds;
    /** STATUS | ASSIGN (replace assignees) | ADD_ASSIGNEE (keep current, add these) */
    @NotBlank
    private String action;
    private TaskStatus status;
    private String reason;
    private List<Long> employeeIds;
    private com.mentis.hrms.pm.enums.TaskPriority priority;
    private Long sprintId;
}