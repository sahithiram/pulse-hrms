package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.TaskAttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TaskAttachmentService {
    TaskAttachmentResponse upload(Long taskId, MultipartFile file, Long userId);
    List<TaskAttachmentResponse> list(Long taskId);
    Resource download(Long id);
    String downloadFileName(Long id);
    void delete(Long id, Long userId);

    /** task that owns the attachment (used for permission checks) */
    Long taskIdOf(Long attachmentId);
}