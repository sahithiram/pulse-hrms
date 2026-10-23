package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CompleteSprintRequest;
import com.mentis.hrms.pm.dto.request.CreateSprintRequest;
import com.mentis.hrms.pm.dto.request.UpdateRetroRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.BurndownResponse;
import com.mentis.hrms.pm.dto.response.SprintResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.dto.response.VelocityResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.SprintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmSprintController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final PmAccessService pmAccessService;

    private Long projectOf(Long sprintId) {
        return sprintService.get(sprintId).getProjectId();
    }

    @PostMapping("/projects/{projectId}/sprints")
    public ResponseEntity<ApiResponse<SprintResponse>> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateSprintRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Sprint created", sprintService.create(projectId, req, userId)));
    }

    @GetMapping("/projects/{projectId}/sprints")
    public ResponseEntity<ApiResponse<List<SprintResponse>>> list(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        pmAccessService.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(sprintService.listByProject(projectId)));
    }

    /** Average of the last 3 completed sprints — a starting point for the next commitment. */
    @GetMapping("/projects/{projectId}/velocity")
    public ResponseEntity<ApiResponse<VelocityResponse>> velocity(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        pmAccessService.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(sprintService.velocity(projectId)));
    }

    @GetMapping("/sprints/{id}")
    public ResponseEntity<ApiResponse<SprintResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        SprintResponse s = sprintService.get(id);
        pmAccessService.requireCanViewProject(userId, s.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(s));
    }

    @PutMapping("/sprints/{id}")
    public ResponseEntity<ApiResponse<SprintResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateSprintRequest req,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(sprintService.update(id, req, userId)));
    }

    @PostMapping("/sprints/{id}/start")
    public ResponseEntity<ApiResponse<SprintResponse>> start(
            @PathVariable Long id, @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok("Sprint started", sprintService.start(id, userId)));
    }

    /** Body is optional: {"carryOverSprintId": 12, "retroNotes": "..."} */
    @PostMapping("/sprints/{id}/complete")
    public ResponseEntity<ApiResponse<SprintResponse>> complete(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid CompleteSprintRequest body,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok("Sprint completed", sprintService.complete(id, body, userId)));
    }

    /** Retro notes can be written / edited at any time, also after the sprint is closed. */
    @PutMapping("/sprints/{id}/retro")
    public ResponseEntity<ApiResponse<SprintResponse>> retro(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRetroRequest body,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok("Notes saved",
                sprintService.updateRetro(id, body.getRetroNotes(), userId)));
    }

    @DeleteMapping("/sprints/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        sprintService.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @GetMapping("/sprints/{id}/tasks")
    public ResponseEntity<ApiResponse<List<TaskSummaryResponse>>> tasks(
            @PathVariable Long id, @CurrentUserId Long userId) {
        pmAccessService.requireCanViewProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(sprintService.sprintTasks(id)));
    }

    @PostMapping("/sprints/{id}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Void>> addTask(
            @PathVariable Long id, @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        sprintService.addTaskToSprint(id, taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Task added", null));
    }

    @DeleteMapping("/sprints/{id}/tasks/{taskId}")
    public ResponseEntity<ApiResponse<Void>> removeTask(
            @PathVariable Long id, @PathVariable Long taskId,
            @CurrentUserId Long userId) {
        pmAccessService.requireCanManageProject(userId, projectOf(id));
        sprintService.removeTaskFromSprint(id, taskId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Task removed", null));
    }

    @GetMapping("/sprints/{id}/burndown")
    public ResponseEntity<ApiResponse<BurndownResponse>> burndown(
            @PathVariable Long id, @CurrentUserId Long userId) {
        pmAccessService.requireCanViewProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(sprintService.burndown(id)));
    }

    @GetMapping("/backlog")
    public ResponseEntity<ApiResponse<List<TaskSummaryResponse>>> backlog(
            @RequestParam(required = false) Long projectId,
            @CurrentUserId Long userId) {
        if (projectId == null) pmAccessService.requireAdmin(userId);
        else pmAccessService.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(sprintService.backlog(projectId)));
    }
}