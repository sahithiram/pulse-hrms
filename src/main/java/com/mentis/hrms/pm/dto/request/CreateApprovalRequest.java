package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateApprovalRequest {
    @NotNull private String entityType;   // TASK | PROJECT | BUDGET | TIMESHEET
    @NotNull private Long entityId;
    @NotNull private Long approverId;
    private String reason;
    private String priority;              // LOW | NORMAL | HIGH

    // ─── Step 2.2 addition ────────────────────────────────────
    // PROJECT: {"toStatus":"ACTIVE"}    BUDGET: {"newBudget":150000}
    private String payload;
}