package com.mentis.hrms.pm.dto.response;

import java.time.LocalDateTime;

public record ProjectDocumentResponse(
        Long id, Long projectId, String category, String title,
        String kind,            // FILE or LINK
        String linkUrl, String fileName, Long fileSize,
        String uploadedByName, LocalDateTime uploadedAt) {}