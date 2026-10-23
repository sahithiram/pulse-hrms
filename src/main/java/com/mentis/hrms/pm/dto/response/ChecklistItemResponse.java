package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChecklistItemResponse {
    private Long id;
    private Long taskId;
    private Long parentId;
    private String title;
    private Boolean done;
    private Long assigneeId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private List<ChecklistItemResponse> children;
}