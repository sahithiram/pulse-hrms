package com.mentis.hrms.pm.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Only the moves the user ticked. from / to are the window the preview used (echoed back so
 * the 85% re-check at apply time looks at the same period). fromEmployeeId is optional for
 * single-assignee tasks and required when a task has several assignees.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalanceApplyRequest {

    private LocalDate from;
    private LocalDate to;

    @NotEmpty @Size(max = 100) @Valid
    private List<Move> moves;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Move {
        @NotNull private Long taskId;
        @NotNull private Long toEmployeeId;
        private Long fromEmployeeId;
    }
}