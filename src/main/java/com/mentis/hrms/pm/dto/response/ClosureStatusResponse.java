package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClosureStatusResponse {
    private Long projectId;
    private String projectStatus;
    private boolean clientRequired;
    /** NOT_REQUIRED | NOT_REQUESTED | PENDING | APPROVED | REJECTED */
    private String signOffState;
    private Long signOffApprovalId;
    private boolean lessonsLearnedWritten;
    private long openTasks;
    private long totalItems;
    private long doneItems;
    /** true = every rule passes and the project can be moved to COMPLETED */
    private boolean ready;
    private List<String> blockers;
    private List<ClosureItemResponse> items;
}