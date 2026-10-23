package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ActivityResponse {
    private Long id;
    private String entityType;
    private Long entityId;
    private String action;
    private Long actorId;
    private String actorName;
    private String oldValue;
    private String newValue;
    private String message;
    private LocalDateTime createdAt;
}