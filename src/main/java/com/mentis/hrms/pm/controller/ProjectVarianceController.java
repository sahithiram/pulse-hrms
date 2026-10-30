package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.VarianceReport;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectService;
import com.mentis.hrms.pm.service.ProjectVarianceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController("pmProjectVarianceController")
@RequestMapping("/api/pm/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectVarianceController {

    private final ProjectVarianceService service;
    private final ProjectService projectService;
    private final PmAccessService access;

    @GetMapping("/variance")
    public ResponseEntity<ApiResponse<VarianceReport>> variance(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.report(projectId)));
    }

    @PostMapping("/baseline/tasks")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> capture(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "false") boolean overwrite,
            @RequestParam(required = false) String reason,
            @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, projectId);
        if (access.isProjectLocked(projectId))
            throw new BusinessRuleException(
                    "This project is completed or archived, so its baseline is locked.");
        if (overwrite) {
            // Re-baseline: project method archives history, then captures tasks with overwrite.
            projectService.saveBaseline(projectId, userId, reason);
            VarianceReport report = service.report(projectId);
            int n = report.getRows() == null ? 0
                    : (int) report.getRows().stream().filter(r -> !r.isAddedAfterBaseline()).count();
            return ResponseEntity.ok(ApiResponse.ok("Baseline captured", Map.of("tasks", n)));
        }
        return ResponseEntity.ok(ApiResponse.ok("Baseline captured",
                Map.of("tasks", service.captureBaseline(projectId, false, userId))));
    }
}