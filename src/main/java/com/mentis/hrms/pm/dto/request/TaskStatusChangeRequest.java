package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.TaskStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusChangeRequest {

    @NotNull
    private TaskStatus status;

    // ─── Step 2.2 / 3.2D addition ────────────────────────────
    /** Required when status = BLOCKED. Captured as a comment. */
    private String reason;
}