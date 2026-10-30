package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.HandoverRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.HandoverResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.HandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("pmHandoverController")
@RequestMapping("/api/pm/handover")
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService service;
    private final PmAccessService access;

    /**
     * project set -> owner / project manager / Super Admin (same as adding or removing members)
     * no project  -> Super Admin only (HR is read-only on projects in your rules)
     */
    private void requireAllowed(Long userId, Long projectId) {
        if (projectId != null) {
            access.requireCanAdminister(userId, projectId);
        } else if (!access.isSuperAdmin(userId)) {
            throw new PmAccessDeniedException(
                    "Only a Super Admin can hand over work across all projects.");
        }
    }

    @GetMapping("/open-tasks")
    public ResponseEntity<ApiResponse<List<HandoverResponse.Row>>> openTasks(
            @RequestParam Long employeeId,
            @RequestParam(required = false) Long projectId,
            @CurrentUserId Long userId) {
        // HR may PREVIEW across projects (read-only); only reassigning is restricted
        if (projectId != null) access.requireCanAdminister(userId, projectId);
        else if (!access.isAdmin(userId))
            throw new PmAccessDeniedException("Only HR or a Super Admin can preview all projects.");
        return ResponseEntity.ok(ApiResponse.ok(service.openTasks(employeeId, projectId)));
    }

    @PostMapping("/team/{teamId}/lead")
    public ResponseEntity<ApiResponse<Void>> replaceLead(
            @PathVariable Long teamId,
            @RequestBody Map<String, Long> body,
            @CurrentUserId Long userId) {
        access.requireCanManageTeam(userId, teamId);   // Super Admin + the team's creator
        Long newLead = body.get("newLeadEmployeeId");
        if (newLead == null) throw new IllegalArgumentException("newLeadEmployeeId is required");
        service.replaceTeamLead(teamId, newLead, userId);
        return ResponseEntity.ok(ApiResponse.ok("Team lead replaced", null));
    }

    @PostMapping("/reassign")
    public ResponseEntity<ApiResponse<HandoverResponse>> reassign(
            @Valid @RequestBody HandoverRequest req, @CurrentUserId Long userId) {
        requireAllowed(userId, req.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok("Handover done", service.reassign(req, userId)));
    }


}