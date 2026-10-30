package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.ProjectDocumentResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.ProjectDocument;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ProjectDocumentRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectDocumentService {

    private static final long MAX_BYTES = 20L * 1024 * 1024;
    public  static final long MAX_BYTES_PUBLIC = MAX_BYTES;
    private static final Set<String> CATEGORIES =
            Set.of("SOW", "SPEC", "CONTRACT", "DESIGN", "REPORT", "LINK", "OTHER");
    private static final Set<String> BLOCKED_EXT =
            Set.of("exe", "bat", "cmd", "msi", "scr", "jar", "sh", "ps1", "vbs", "dll");

    private final ProjectDocumentRepository repo;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PmAccessService access;

    @Value("${app.upload.base-path:C:/hrms/uploads}")
    private String basePath;

    @Transactional(readOnly = true)
    public List<ProjectDocumentResponse> list(Long projectId) {
        return repo.findByProjectIdOrderByUploadedAtDesc(projectId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public ProjectDocumentResponse addFile(Long projectId, String category, String title,
                                           MultipartFile file, Long userId) {
        requireProject(projectId);
        if (file == null || file.isEmpty()) throw new BusinessRuleException("File is empty");
        if (file.getSize() > MAX_BYTES) throw new BusinessRuleException("File exceeds 20 MB limit");

        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = extOf(original);
        if (BLOCKED_EXT.contains(ext))
            throw new BusinessRuleException("This file type is not allowed: ." + ext);

        // generated name = UUID + "." + safe extension only — never the client name
        String stored = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path dir = Paths.get(basePath, "pm", "project-" + projectId);
        Path dest = dir.resolve(stored).normalize();
        if (!dest.startsWith(Paths.get(basePath).normalize()))
            throw new BusinessRuleException("Invalid storage path");
        try {
            Files.createDirectories(dir);
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store file: " + e.getMessage());
        }

        ProjectDocument d = repo.save(ProjectDocument.builder()
                .projectId(projectId).category(cat(category)).title(cleanTitle(title, original))
                .fileName(original).filePath("pm/project-" + projectId + "/" + stored)
                .fileSize(file.getSize()).contentType(file.getContentType())
                .uploadedBy(userId).build());
        log(projectId, "DOCUMENT_ADDED", userId, d.getTitle());
        return toResponse(d);
    }

    @Transactional
    public ProjectDocumentResponse addLink(Long projectId, String category, String title,
                                           String url, Long userId) {
        requireProject(projectId);
        String u = url == null ? "" : url.trim();
        try {
            URI uri = URI.create(u);
            String s = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!s.equals("http") && !s.equals("https") || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Enter a valid http:// or https:// link");
        }
        if (u.length() > 1000) throw new BusinessRuleException("Link is too long (max 1000 characters)");

        ProjectDocument d = repo.save(ProjectDocument.builder()
                .projectId(projectId).category(cat(category == null ? "LINK" : category))
                .title(cleanTitle(title, u)).linkUrl(u).uploadedBy(userId).build());
        log(projectId, "DOCUMENT_LINK_ADDED", userId, d.getTitle());
        return toResponse(d);
    }

    @Transactional
    public void delete(Long projectId, Long id, Long userId) {
        ProjectDocument d = find(projectId, id);
        if (!userId.equals(d.getUploadedBy()) && !access.canAdministerProject(userId, projectId))
            throw new PmAccessDeniedException(
                    "Only the person who added this document, the owner or a project manager can delete it.");
        repo.delete(d);
        if (d.getFilePath() != null) {
            try {
                Path p = safeResolve(d.getFilePath());
                if (p != null) Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("Could not delete {}: {}", d.getFilePath(), e.getMessage());
            }
        }
        log(projectId, "DOCUMENT_DELETED", userId, d.getTitle());
    }

    @Transactional(readOnly = true)
    public Resource download(Long projectId, Long id) {
        ProjectDocument d = find(projectId, id);
        if (d.getFilePath() == null) throw new BusinessRuleException("This document is a link, not a file");
        Path p = safeResolve(d.getFilePath());
        if (p == null || !Files.exists(p))
            throw new ResourceNotFoundException("File missing on disk: " + d.getFileName());
        return new FileSystemResource(p);
    }

    @Transactional(readOnly = true)
    public String fileNameOf(Long projectId, Long id) {
        return find(projectId, id).getFileName();
    }

    public java.util.Map<String, Object> limits() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("maxBytes", MAX_BYTES);
        m.put("categories", new java.util.TreeSet<>(CATEGORIES));
        m.put("blockedExtensions", new java.util.TreeSet<>(BLOCKED_EXT));
        return m;
    }

    /** Public so the controller can fill the uploader's display name without N+1. */
    public String displayName(Long pk) {
        return nameOf(pk);
    }

    // ───────── helpers ─────────

    private Path safeResolve(String relative) {
        try {
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path p = base.resolve(relative).normalize();
            return p.startsWith(base) ? p : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extOf(String name) {
        String n = name == null ? "" : name;
        int i = n.lastIndexOf('.');
        if (i < 0 || i == n.length() - 1) return "";
        String e = n.substring(i + 1).toLowerCase(Locale.ROOT);
        if (e.contains("/") || e.contains("\\") || e.contains(".")) return "";
        return e;
    }

    private ProjectDocument find(Long projectId, Long id) {
        ProjectDocument d = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + id));
        if (!projectId.equals(d.getProjectId()))
            throw new ResourceNotFoundException("Document not found: " + id);
        return d;
    }

    private void requireProject(Long projectId) {
        if (!projectRepository.existsById(projectId))
            throw new ResourceNotFoundException("Project not found: " + projectId);
    }

    private String cat(String c) {
        String x = c == null || c.isBlank() ? "OTHER" : c.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(x)) throw new BusinessRuleException("Category must be one of " + CATEGORIES);
        return x;
    }

    private String cleanTitle(String t, String fallback) {
        String x = t == null || t.isBlank() ? fallback : t.trim();
        return x.length() > 200 ? x.substring(0, 200) : x;
    }

    private void log(Long projectId, String action, Long userId, String title) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("PROJECT").entityId(projectId).action(action)
                    .actorId(userId).actorName(nameOf(userId))
                    .newValue(title).message(action.replace('_', ' ').toLowerCase() + ": " + title).build());
        } catch (Exception e) {
            log.warn("document activity log failed: {}", e.getMessage());
        }
    }

    private String nameOf(Long pk) {
        if (pk == null) return "System";
        return employeeRepository.findById(pk).map(e -> {
            String f = e.getFirstName() == null ? "" : e.getFirstName();
            String l = e.getLastName() == null ? "" : e.getLastName();
            String full = (f + " " + l).trim();
            return full.isEmpty() ? "Employee #" + pk : full;
        }).orElse("Employee #" + pk);
    }

    private ProjectDocumentResponse toResponse(ProjectDocument d) {
        return new ProjectDocumentResponse(d.getId(), d.getProjectId(), d.getCategory(), d.getTitle(),
                d.getLinkUrl() != null ? "LINK" : "FILE", d.getLinkUrl(), d.getFileName(), d.getFileSize(),
                nameOf(d.getUploadedBy()), d.getUploadedAt());
    }
}