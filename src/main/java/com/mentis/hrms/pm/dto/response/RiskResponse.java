package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private String title;
    private String description;
    private String likelihood;
    private String impact;
    private Integer score;
    private String severity;   // computed: HIGH (>=15) | MEDIUM (>=9) | LOW
    private String status;
    private Long ownerId;
    private String ownerName;
    private String mitigation;
    private LocalDate reviewDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}