package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class KeyResultResponse {
    private Long id;
    private Long goalId;
    private String metric;
    private String unit;
    private Double targetValue;
    private Double currentValue;
    private Double baselineValue;
    private Integer progressPct;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}