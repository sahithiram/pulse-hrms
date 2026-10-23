package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.ProjectRequestDecision;
import com.mentis.hrms.pm.dto.request.SubmitProjectRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ProjectRequestResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectIntakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmProjectIntakeController")
@RequestMapping("/api/pm/project-requests")
@RequiredArgsConstructor
public class ProjectIntakeController {

    private final ProjectIntakeService service;
    private final PmAccessService access;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> submit(
            @RequestBody SubmitProjectRequest req, @CurrentUserId Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Request submitted", service.submit(req, userId)));
    }

    /** Admins see every request, everyone else sees their own. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectRequestResponse>>> list(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(userId, access.isAdmin(userId))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id, userId, access.isAdmin(userId))));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> approve(
            @PathVariable Long id, @RequestBody(required = false) ProjectRequestDecision body,
            @CurrentUserId Long userId) {
        access.requireAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok("Request approved, project created", service.approve(id, body, userId)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> reject(
            @PathVariable Long id, @RequestBody(required = false) ProjectRequestDecision body,
            @CurrentUserId Long userId) {
        access.requireAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok("Request rejected", service.reject(id, body, userId)));
    }
}