package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.TaskAttachmentResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController("pmTaskAttachmentController")
@RequestMapping("/api/pm/tasks")
@RequiredArgsConstructor
public class TaskAttachmentController {

    private final TaskAttachmentService service;
    private final PmAccessService access;

    @PostMapping(value = "/{taskId}/attachments",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TaskAttachmentResponse>> upload(
            @PathVariable Long taskId,
            @RequestPart("file") MultipartFile file,
            @CurrentUserId Long userId) {
        access.requireCanEdit(userId, taskId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Uploaded", service.upload(taskId, file, userId)));
    }

    @GetMapping("/{taskId}/attachments")
    public ResponseEntity<ApiResponse<List<TaskAttachmentResponse>>> list(
            @PathVariable Long taskId, @CurrentUserId Long userId) {
        access.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(service.list(taskId)));
    }

    @GetMapping("/attachments/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanView(userId, service.taskIdOf(id));
        Resource r = service.download(id);
        String safeName = service.downloadFileName(id).replace("\"", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeName + "\"")
                .body(r);
    }

    @DeleteMapping("/attachments/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanEdit(userId, service.taskIdOf(id));
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}