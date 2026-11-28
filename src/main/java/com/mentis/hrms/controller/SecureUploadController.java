package com.mentis.hrms.controller;

import com.mentis.hrms.service.AuditService;
import com.mentis.hrms.util.Roles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.Set;

/**
 * Replaces the public /uploads/** file serving.\n * Access rule (default-deny): HR / SUPER_ADMIN may read any upload; everyone else only files whose\n * name starts with their own employeeId (e.g. "EMP012-..."). Extend via DocumentOwnershipResolver\n * once documents carry an owner mapping.\n * Filenames are sanitized — no path traversal possible.\n */
@RestController
@RequestMapping("/secure-uploads")
public class SecureUploadController {

    private final Path uploadRoot;
    private final AuditService auditService;

    public SecureUploadController(
            @Value("${app.upload.dir:./uploads}") String uploadDir,
            AuditService auditService) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.auditService = auditService;
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<Resource> download(@PathVariable String filename,
                                             jakarta.servlet.http.HttpSession session,
                                             HttpServletRequest request) throws IOException {
        Object userId = session == null ? null : session.getAttribute("userId");
        String role = session == null ? null : (String) session.getAttribute("userRole");
        String employeeId = session == null ? null : (String) session.getAttribute("employeeId");

        if (userId == null) return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).build();

        // Sanitize: basename only, reject separators/traversal
        String safe = Paths.get(filename).getFileName().toString();
        if (!safe.equals(filename) || safe.contains("..")) {
            return ResponseEntity.badRequest().build();
        }

        Path target = uploadRoot.resolve(safe).normalize();
        if (!target.startsWith(uploadRoot) || !Files.exists(target) || !Files.isRegularFile(target)) {
            return ResponseEntity.notFound().build();
        }

        boolean allowed = Roles.isAnyHrOrSuper(role)
                || (employeeId != null && !employeeId.isBlank() && safe.startsWith(employeeId + "-"))
                || (employeeId != null && !employeeId.isBlank() && safe.startsWith(employeeId + "_"));

        auditService.audit("FILE_ACCESS",
                userId instanceof Number n ? ((Number) userId).longValue() : null,
                role, "file", safe, null, null, request,
                "{\"allowed\":" + allowed + ",\"ts\":\"" + Instant.now() + "\"}");

        if (!allowed) return ResponseEntity.status(403).build();

        Resource resource = new UrlResource(target.toUri());
        String contentType = Files.probeContentType(target);
        MediaType mediaType = contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safe + "\"")
                .contentType(mediaType)
                .contentLength(Files.size(target))
                .body(resource);
    }

}
