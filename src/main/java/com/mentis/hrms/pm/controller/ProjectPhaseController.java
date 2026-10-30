package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreatePhaseRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ProjectPhaseResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectPhaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * PUT/DELETE live here because {@link ProjectController} is mapped to /api/pm/projects.
 * GET/POST for a project's phases are on ProjectController, matching the milestone URL shape.
 */
@RestController("pmProjectPhaseController")
@RequestMapping("/api/pm/phases")
@RequiredArgsConstructor
public class ProjectPhaseController {

    private final ProjectPhaseService service;
    private final PmAccessService access;

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectPhaseResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreatePhaseRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, service.get(id).getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, service.get(id).getProjectId());
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}
