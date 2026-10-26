package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateTemplateRequest;
import com.mentis.hrms.pm.dto.request.FromTemplateRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectTemplateResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmProjectTemplateController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class ProjectTemplateController {

    private final ProjectTemplateService service;
    private final PmAccessService access;

    @GetMapping("/project-templates")
    public ResponseEntity<ApiResponse<List<ProjectTemplateResponse>>> list(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list()));
    }

    @GetMapping("/project-templates/{id}")
    public ResponseEntity<ApiResponse<ProjectTemplateResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id)));
    }

    /** A Super Admin or HR Manager defines templates (same people who decide project requests). */
    @PostMapping("/project-templates")
    public ResponseEntity<ApiResponse<ProjectTemplateResponse>> create(
            @Valid @RequestBody CreateTemplateRequest req, @CurrentUserId Long userId) {
        access.requireIntakeReviewer(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Template saved", service.create(req, userId)));
    }

    /** Same permission as creating a project directly. */
    @PostMapping("/projects/from-template/{templateId}")
    public ResponseEntity<ApiResponse<ProjectResponse>> fromTemplate(
            @PathVariable Long templateId, @RequestBody FromTemplateRequest req, @CurrentUserId Long userId) {
        access.requireCanCreateProject(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Project created from template", service.instantiate(templateId, req, userId)));
    }
}