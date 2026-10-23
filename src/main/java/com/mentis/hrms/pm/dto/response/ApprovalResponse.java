package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalResponse {
    private Long id;
    private String entityType;
    private Long entityId;
    private String entityTitle;
    private Long requestedBy;
    private String requestedByName;
    private Long approverId;
    private String approverName;
    private String status;
    private String reason;
    private String decisionNote;
    private Long decidedBy;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ─── Step 2.2 addition ────────────────────────────────────
    private String payload;
}