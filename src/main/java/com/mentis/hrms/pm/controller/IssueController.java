package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateIssueRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.IssueResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.IssueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("pmIssueController")
@RequestMapping("/api/pm/issues")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService service;
    private final PmAccessService access;

    private void requireCanWorkOnIssue(Long userId, IssueResponse i) {
        if (access.canManageProject(userId, i.getProjectId())) return;
        if (userId.equals(i.getAssigneeId()) || userId.equals(i.getReportedBy())) return;
        throw new PmAccessDeniedException("Only the project manager, assignee or reporter can change this issue.");
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IssueResponse>> create(
            @Valid @RequestBody CreateIssueRequest req,
            @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, req.getProjectId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Issue created", service.create(req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IssueResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        IssueResponse i = service.get(id);
        access.requireCanViewProject(userId, i.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(i));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> listByProject(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.listByProject(projectId)));
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity<ApiResponse<List<IssueResponse>>> listByTask(
            @PathVariable Long taskId, @CurrentUserId Long userId) {
        access.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(service.listByTask(taskId)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<IssueResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @CurrentUserId Long userId) {
        requireCanWorkOnIssue(userId, service.get(id));
        return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, body.get("status"), userId)));
    }

    @PostMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<IssueResponse>> assign(
            @PathVariable Long id,
            @RequestBody Map<String, Long> body,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, service.get(id).getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(service.assign(id, body.get("assigneeId"), userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, service.get(id).getProjectId());
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}