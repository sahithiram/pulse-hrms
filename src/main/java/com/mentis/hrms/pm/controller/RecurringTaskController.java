package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.RecurringTaskService;
import com.mentis.hrms.pm.service.RecurringTaskService.RuleRequest;
import com.mentis.hrms.pm.service.RecurringTaskService.RuleView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("pmRecurringTaskController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class RecurringTaskController {

    private final RecurringTaskService service;

    @GetMapping("/projects/{projectId}/recurring")
    public ResponseEntity<ApiResponse<List<RuleView>>> list(@PathVariable Long projectId, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.list(projectId, userId)));
    }

    @PostMapping("/projects/{projectId}/recurring")
    public ResponseEntity<ApiResponse<RuleView>> create(
            @PathVariable Long projectId, @RequestBody RuleRequest q, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Recurring task created", service.create(projectId, q, userId)));
    }

    @PutMapping("/recurring/{id}")
    public ResponseEntity<ApiResponse<RuleView>> update(
            @PathVariable Long id, @RequestBody RuleRequest q, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", service.update(id, q, userId)));
    }

    @DeleteMapping("/recurring/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @PostMapping("/recurring/{id}/pause")
    public ResponseEntity<ApiResponse<RuleView>> pause(@PathVariable Long id, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Paused", service.setPaused(id, true, userId)));
    }

    @PostMapping("/recurring/{id}/resume")
    public ResponseEntity<ApiResponse<RuleView>> resume(@PathVariable Long id, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Resumed", service.setPaused(id, false, userId)));
    }

    @PostMapping("/projects/{projectId}/recurring/run-now")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> runNow(@PathVariable Long projectId, @CurrentUserId Long userId) {
        int n = service.runProject(projectId, userId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("created", n)));
    }
}