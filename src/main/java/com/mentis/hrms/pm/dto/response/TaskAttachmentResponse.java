package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskAttachmentResponse {
    private Long id;
    private Long taskId;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String contentType;
    private Long uploadedBy;
    private LocalDateTime uploadedAt;
}