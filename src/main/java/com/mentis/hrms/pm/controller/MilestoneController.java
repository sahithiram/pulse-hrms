package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateMilestoneRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.MilestoneResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmMilestoneController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService service;
    private final PmAccessService access;

    private Long projectOf(Long milestoneId) {
        return service.get(milestoneId).getProjectId();
    }

    @PostMapping("/projects/{projectId}/milestones")
    public ResponseEntity<ApiResponse<MilestoneResponse>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateMilestoneRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Milestone created", service.create(projectId, req, userId)));
    }

    @GetMapping("/projects/{projectId}/milestones")
    public ResponseEntity<ApiResponse<List<MilestoneResponse>>> list(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.listByProject(projectId)));
    }

    @GetMapping("/milestones/{id}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        MilestoneResponse m = service.get(id);
        access.requireCanViewProject(userId, m.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PutMapping("/milestones/{id}")
    public ResponseEntity<ApiResponse<MilestoneResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateMilestoneRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req, userId)));
    }

    @PostMapping("/milestones/{id}/complete")
    public ResponseEntity<ApiResponse<MilestoneResponse>> complete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok("Completed", service.complete(id, userId)));
    }

    @DeleteMapping("/milestones/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @GetMapping("/milestones/{id}/tasks")
    public ResponseEntity<ApiResponse<List<TaskSummaryResponse>>> tasks(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(service.milestoneTasks(id)));
    }

    @PostMapping("/milestones/{id}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Void>> addTask(
            @PathVariable Long id, @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        service.addTaskToMilestone(id, taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Task added", null));
    }

    @DeleteMapping("/milestones/{id}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Void>> removeTask(
            @PathVariable Long id, @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        service.removeTaskFromMilestone(id, taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Task removed", null));
    }
}