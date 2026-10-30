package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalancePreviewResponse {

    private LocalDate from;
    private LocalDate to;
    private int overCount;          // people currently OVER in the window
    private int maxMoves;           // the cap that was applied
    private boolean capped;         // true when the cap stopped the proposal early
    private List<Move> moves;
    private List<PersonDelta> people;   // everyone touched, before -> after
    private List<String> notes;         // why someone is still over, etc.

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Move {
        private Long taskId;
        private String title;
        private Long projectId;
        private String projectName;
        private double hours;
        private Long fromEmployeeId;
        private String fromName;
        private Long toEmployeeId;
        private String toName;
        private int fromBeforePct;
        private int fromAfterPct;
        private int toBeforePct;
        private int toAfterPct;
        private String reason;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PersonDelta {
        private Long employeeId;
        private String name;
        private int beforePct;
        private int afterPct;
    }
}