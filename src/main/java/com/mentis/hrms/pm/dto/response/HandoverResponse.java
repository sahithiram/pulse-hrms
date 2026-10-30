package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class HandoverResponse {
    private int moved;
    private boolean memberRemoved;
    private List<Row> tasks;
    private List<String> skipped;

    @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Row {
        private Long taskId;
        private Long projectId;
        private String projectName;
        private String title;
        private String status;
        private LocalDate dueDate;
    }
}