package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.GoalStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GoalResponse {
    private Long id;
    private Long projectId;
    private String projectName;
    private Long ownerId;
    private String ownerName;
    private String title;
    private String description;
    private String quarter;
    private Integer year;
    private GoalStatus status;
    private Integer progressPct;
    private List<KeyResultResponse> keyResults;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}