package com.mentis.hrms.pm.dto.response;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskBoardMetaResponse {
    private Long taskId;
    private long subtaskCount;
    private long subtaskDone;
    private long checklistTotal;
    private long checklistDone;
    private long openBlockerCount;
    private long dependencyCount;
}