package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClosureItemResponse {
    private Long id;
    private Long projectId;
    private String title;
    private boolean done;
    private Long doneBy;
    private String doneByName;
    private LocalDateTime doneAt;
    private LocalDateTime createdAt;
}