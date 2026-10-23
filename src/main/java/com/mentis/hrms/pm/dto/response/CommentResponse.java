package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CommentResponse {
    private Long id;
    private Long taskId;
    private Long authorId;
    private String authorName;
    private String body;
    private String mentions;
    private Boolean edited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}