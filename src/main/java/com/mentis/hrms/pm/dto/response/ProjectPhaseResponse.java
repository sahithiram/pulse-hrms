package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.ProjectPhaseStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectPhaseResponse {
    private Long id;
    private Long projectId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private ProjectPhaseStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
