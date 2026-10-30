package com.mentis.hrms.pm.dto.request;

import lombok.*;

import java.time.LocalDate;

/** Body is optional. Anything left out falls back to the action item's own values; no sprint = backlog. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConvertRetroItemRequest {
    private Long assigneeId;
    private LocalDate dueDate;
    /** a planned or active sprint of the same project; null puts the task in the backlog */
    private Long sprintId;
}