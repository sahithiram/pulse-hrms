package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ProjectDocumentResponse;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("pmProjectDocumentController")
@RequestMapping("/api/pm/projects/{projectId}/documents")
@RequiredArgsConstructor
public class ProjectDocumentController {

    public record LinkBody(String category, String title, String url) {}

    private final ProjectDocumentService service;
    private final PmAccessService access;
    private final ProjectMemberRepository memberRepo;

    private boolean canWrite(Long userId, Long projectId) {
        if (access.isProjectLocked(projectId)) return false;
        return memberRepo.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> !"VIEWER".equals(m.getRole().name()))
                .orElse(access.isAdmin(userId));
    }

    private void requireWrite(Long userId, Long projectId) {
        access.requireCanViewProject(userId, projectId);
        if (access.isProjectLocked(projectId))
            throw new BusinessRuleException("This project is completed or archived, so documents are locked.");
        if (!canWrite(userId, projectId))
            throw new PmAccessDeniedException("Viewers can't add or delete documents.");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectDocumentResponse>>> list(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.list(projectId)));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> config(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        Map<String, Object> m = new LinkedHashMap<>(service.limits());
        m.put("canWrite", canWrite(userId, projectId));
        m.put("canAdminister", access.canAdministerProject(userId, projectId));
        m.put("me", userId);
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ProjectDocumentResponse>> upload(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "title", required = false) String title,
            @CurrentUserId Long userId) {
        requireWrite(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok("Uploaded", service.addFile(projectId, category, title, file, userId)));
    }

    @PostMapping("/link")
    public ResponseEntity<ApiResponse<ProjectDocumentResponse>> link(
            @PathVariable Long projectId, @RequestBody LinkBody b, @CurrentUserId Long userId) {
        requireWrite(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok("Link added",
                service.addLink(projectId, b.category(), b.title(), b.url(), userId)));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long projectId, @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        Resource r = service.download(projectId, id);
        String name = service.fileNameOf(projectId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(r);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long projectId, @PathVariable Long id, @CurrentUserId Long userId) {
        requireWrite(userId, projectId);
        service.delete(projectId, id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}