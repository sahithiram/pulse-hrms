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

    /** Any signed-in employee may submit an idea. */
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> submit(
            @RequestBody SubmitProjectRequest req, @CurrentUserId Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Request submitted", service.submit(req, userId)));
    }

    /** Admins (Super Admin, HR) and PMs read the whole queue; everyone else sees their own. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectRequestResponse>>> list(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(userId, access.canViewIntakeQueue(userId))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(id, userId, access.canViewIntakeQueue(userId))));
    }

    /** Only a Super Admin or HR Manager decides. Plain HR and PMs are read-only. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> approve(
            @PathVariable Long id, @RequestBody(required = false) ProjectRequestDecision body,
            @CurrentUserId Long userId) {
        access.requireIntakeReviewer(userId);
        return ResponseEntity.ok(ApiResponse.ok("Request approved, project created", service.approve(id, body, userId)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> reject(
            @PathVariable Long id, @RequestBody(required = false) ProjectRequestDecision body,
            @CurrentUserId Long userId) {
        access.requireIntakeReviewer(userId);
        return ResponseEntity.ok(ApiResponse.ok("Request rejected", service.reject(id, body, userId)));
    }

    /** STEP 2: the requester takes their own request back (service checks ownership and status). */
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<ApiResponse<ProjectRequestResponse>> withdraw(
            @PathVariable Long id, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Request withdrawn", service.withdraw(id, userId)));
    }
}