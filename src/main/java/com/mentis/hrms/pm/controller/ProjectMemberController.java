package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.AddMemberRequest;
import com.mentis.hrms.pm.dto.request.UpdateMemberRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ProjectMemberResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("pmProjectMemberController")
@RequestMapping("/api/pm/projects/{projectId}/members")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService service;
    private final PmAccessService access;

    /** Anyone who can see the project can see its members; only managers see hourly rates. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectMemberResponse>>> list(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        boolean showRates = access.canManageProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.list(projectId, showRates)));
    }

    /** What can I do on this project? (used by the UI to show/hide controls) */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myAccess(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        String label = access.isAdmin(userId) ? "Admin"
                : access.isProjectOwner(userId, projectId) ? "Owner"
                : access.isProjectManager(userId, projectId) ? "Manager"
                : access.isProjectViewerOnly(userId, projectId) ? "Viewer"
                : access.isProjectMember(userId, projectId) ? "Member"
                : "Team access";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canManage", access.canManageProject(userId, projectId));
        m.put("label", label);
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> add(
            @PathVariable Long projectId,
            @Valid @RequestBody AddMemberRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Member added", service.add(projectId, req, userId)));
    }

    @PutMapping("/{memberId}")
    public ResponseEntity<ApiResponse<ProjectMemberResponse>> update(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @Valid @RequestBody UpdateMemberRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.update(projectId, memberId, req, userId)));
    }

    @DeleteMapping("/{memberId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectId);
        service.remove(projectId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Removed", null));
    }
}