package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    // ── Step 8: completion summary / reopen info ──
    private LocalDateTime completedAt;
    private String completedByName;
    private LocalDateTime reopenedAt;
    private String reopenedByName;
    private String reopenReason;
    /** The project's planned end date. */
    private LocalDate plannedEnd;
    /** completed day minus planned end: positive = late, negative = early, null = not completed / no end date. */
    private Long delayDays;
    private long tasksTotal;
    private long tasksDone;
}