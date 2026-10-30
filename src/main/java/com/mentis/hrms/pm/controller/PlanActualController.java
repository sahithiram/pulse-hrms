package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.PlanActualResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.PlanActualService;
import com.mentis.hrms.pm.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("pmPlanActualController")
@RequestMapping("/api/pm/projects/{projectId}")
@RequiredArgsConstructor
public class PlanActualController {

    public record BaselineBody(String reason) {}

    private final PlanActualService service;
    private final ProjectService projectService;       // must declare saveBaseline(id,user) and rebaseline(id,reason,user)
    private final PmAccessService access;

    @GetMapping("/plan-actual")
    public ResponseEntity<ApiResponse<PlanActualResponse>> get(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(projectId, userId)));
    }

    private void requireManage(Long userId, Long projectId) {
        access.requireCanViewProject(userId, projectId);
        if (access.isProjectLocked(projectId))
            throw new BusinessRuleException("This project is completed or archived, so its baseline is locked.");
        if (!access.canAdministerProject(userId, projectId))
            throw new PmAccessDeniedException("Only a manager can capture or re-baseline.");
    }

    @PostMapping("/baseline")
    public ResponseEntity<ApiResponse<ProjectResponse>> capture(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        requireManage(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok("Baseline saved", projectService.saveBaseline(projectId, userId)));
    }

    @PostMapping("/rebaseline")
    public ResponseEntity<ApiResponse<ProjectResponse>> rebaseline(
            @PathVariable Long projectId, @RequestBody(required = false) BaselineBody b, @CurrentUserId Long userId) {
        requireManage(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok("Re-baselined",
                projectService.rebaseline(projectId, b == null ? null : b.reason(), userId)));
    }
}