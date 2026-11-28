package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * PM-026 — hand a project over to another owner.
 * Used by POST /api/pm/projects/{id}/owner.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectOwnerChangeRequest {

    /** The employee pk of the new owner (must be an active employee). */
    @NotNull(message = "newOwnerId is required")
    private Long newOwnerId;

    /** Why the hand-over happened. Shown in the activity log and the notification. */
    private String reason;

    /**
     * Keep the previous owner as a MANAGER on the project (default true).
     * Set false when the person is leaving and should lose access immediately.
     */
    private Boolean keepPreviousAsManager;
}
