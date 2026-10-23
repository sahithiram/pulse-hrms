package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.response.TaskAttachmentResponse;
import com.mentis.hrms.pm.entity.TaskAttachment;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.TaskAttachmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.service.TaskAttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskAttachmentServiceImpl implements TaskAttachmentService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final TaskAttachmentRepository repo;
    private final TaskRepository taskRepository;
    private final PmEventPublisher publisher;

    @Value("${app.upload.base-path:C:/hrms/uploads}")
    private String basePath;

    @Override
    @Transactional
    public TaskAttachmentResponse upload(Long taskId, MultipartFile file, Long userId) {
        if (!taskRepository.existsById(taskId))
            throw new ResourceNotFoundException("Task not found: " + taskId);
        if (file == null || file.isEmpty())
            throw new BusinessRuleException("File is empty");
        if (file.getSize() > MAX_BYTES)
            throw new BusinessRuleException("File exceeds 10 MB limit");

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safe = original.replaceAll("[^A-Za-z0-9._-]", "_");
        String stored = UUID.randomUUID() + "-" + safe;

        Path dir = Paths.get(basePath, "pm", "task-" + taskId);
        Path target = dir.resolve(stored);
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store file: " + e.getMessage());
        }

        String relUrl = "pm/task-" + taskId + "/" + stored;
        TaskAttachment saved = repo.save(TaskAttachment.builder()
                .taskId(taskId)
                .fileName(original)
                .fileUrl(relUrl)
                .fileSize(file.getSize())
                .contentType(file.getContentType())
                .uploadedBy(userId)
                .build());

        publisher.publish("POST", "/api/pm/tasks/" + taskId + "/attachments", String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskAttachmentResponse> list(Long taskId) {
        return repo.findByTaskIdOrderByUploadedAtDesc(taskId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(Long id) {
        TaskAttachment a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + id));
        Path p = Paths.get(basePath).resolve(a.getFileUrl());
        if (!Files.exists(p))
            throw new ResourceNotFoundException("File missing on disk: " + a.getFileName());
        return new FileSystemResource(p);
    }

    @Override
    @Transactional(readOnly = true)
    public String downloadFileName(Long id) {
        return repo.findById(id).map(TaskAttachment::getFileName).orElse("file");
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        TaskAttachment a = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + id));
        try {
            Files.deleteIfExists(Paths.get(basePath).resolve(a.getFileUrl()));
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", a.getFileUrl(), e.getMessage());
        }
        repo.delete(a);
        publisher.publish("DELETE", "/api/pm/tasks/attachments/" + id, String.valueOf(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Long taskIdOf(Long attachmentId) {
        return repo.findById(attachmentId)
                .map(TaskAttachment::getTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
    }

    private TaskAttachmentResponse toResponse(TaskAttachment a) {
        return TaskAttachmentResponse.builder()
                .id(a.getId()).taskId(a.getTaskId())
                .fileName(a.getFileName()).fileUrl(a.getFileUrl())
                .fileSize(a.getFileSize()).contentType(a.getContentType())
                .uploadedBy(a.getUploadedBy()).uploadedAt(a.getUploadedAt())
                .build();
    }
}