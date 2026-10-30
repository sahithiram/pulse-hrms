package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.pm.dto.response.TaskAttachmentResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.TaskAttachment;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskAttachmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.security.PmCurrentUserHolder;
import com.mentis.hrms.pm.security.PmUserProvider;
import com.mentis.hrms.pm.service.TaskAttachmentService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskAttachmentServiceImpl implements TaskAttachmentService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> BLOCKED_EXT =
            Set.of("exe", "bat", "cmd", "msi", "scr", "jar", "sh", "ps1", "vbs", "dll");

    private final TaskAttachmentRepository repo;
    private final TaskRepository taskRepository;
    private final PmEventPublisher publisher;
    private final ActivityLogRepository activityLogRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter notificationEmitter;
    private final PmAccessService access;

    @Value("${app.upload.base-path:C:/hrms/uploads}")
    private String basePath;

    @Override
    @Transactional
    public TaskAttachmentResponse upload(Long taskId, MultipartFile file, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (file == null || file.isEmpty())
            throw new BusinessRuleException("File is empty");
        if (file.getSize() > MAX_BYTES)
            throw new BusinessRuleException("File exceeds 10 MB limit");

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = original.contains(".")
                ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (BLOCKED_EXT.contains(ext))
            throw new BusinessRuleException("This file type is not allowed: ." + ext);

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

        logActivity(taskId, "ATTACHMENT_ADDED", userId, null, original, "Attachment added: " + original);
        notifyAssignees(task, userId, "TASK_ATTACHMENT_ADDED",
                "New file on: " + task.getTitle(),
                nameOf(userId) + " attached " + original);

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

        // uploader, or lead tier / owner / manager / super admin
        if (!userId.equals(a.getUploadedBy()) && !access.canAssignTask(userId, a.getTaskId()))
            throw new PmAccessDeniedException(
                    "Only the person who uploaded this file or a project lead can delete it.");

        repo.delete(a);
        try {
            Files.deleteIfExists(Paths.get(basePath).resolve(a.getFileUrl()));
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", a.getFileUrl(), e.getMessage());
        }
        logActivity(a.getTaskId(), "ATTACHMENT_DELETED", userId, a.getFileName(), null,
                "Attachment deleted: " + a.getFileName());
        publisher.publish("DELETE", "/api/pm/tasks/attachments/" + id, String.valueOf(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Long taskIdOf(Long attachmentId) {
        return repo.findById(attachmentId)
                .map(TaskAttachment::getTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
    }

    // ───────────── helpers ─────────────

    private void logActivity(Long taskId, String action, Long actorId,
                             String oldVal, String newVal, String message) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("TASK").entityId(taskId).action(action)
                    .actorId(actorId).actorName(nameOf(actorId))
                    .oldValue(oldVal).newValue(newVal)
                    .message(message != null && message.length() > 480 ? message.substring(0, 480) : message)
                    .build());
        } catch (Exception e) {
            log.warn("attachment activity log failed for task {}: {}", taskId, e.getMessage());
        }
    }

    private void notifyAssignees(Task task, Long actorId, String type, String title, String message) {
        try {
            for (TaskAssignment a : taskAssignmentRepository.findByTaskId(task.getId())) {
                Long pk = a.getEmployeeId();
                if (pk == null || pk.equals(actorId)) continue;
                String code = employeeRepository.findById(pk).map(e -> e.getEmployeeId()).orElse(null);
                if (code == null) continue;
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type(type).action(type)
                        .title(title).message(message)
                        .taskId(task.getId()).projectId(task.getProjectId())
                        .actorId(actorId).actorName(nameOf(actorId))
                        .priority("NORMAL")
                        .deepLink("/pm/03-my-work?task=" + task.getId())
                        .build());
            }
        } catch (Exception e) {
            log.warn("attachment notify failed for task {}: {}", task.getId(), e.getMessage());
        }
    }

    private String nameOf(Long pk) {
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();
        if (u != null && pk != null && pk.equals(u.employeePk())) return u.fullName();
        if (pk == null) return "System";
        return employeeRepository.findById(pk).map(e -> {
            String f = e.getFirstName() == null ? "" : e.getFirstName();
            String l = e.getLastName() == null ? "" : e.getLastName();
            String full = (f + " " + l).trim();
            return full.isEmpty() ? "Employee #" + pk : full;
        }).orElse("Employee #" + pk);
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