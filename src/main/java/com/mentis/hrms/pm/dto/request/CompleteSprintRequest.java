package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CompleteSprintRequest {
    /** Planned sprint of the same project that receives the unfinished tasks. null = back to backlog. */
    private Long carryOverSprintId;
    @Size(max = 10000) private String retroNotes;
}