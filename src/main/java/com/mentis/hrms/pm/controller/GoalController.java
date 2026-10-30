package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateGoalRequest;
import com.mentis.hrms.pm.dto.request.CreateKeyResultRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.GoalResponse;
import com.mentis.hrms.pm.dto.response.KeyResultResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmGoalController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService service;
    private final PmAccessService access;

    @PostMapping("/goals")
    public ResponseEntity<ApiResponse<GoalResponse>> create(
            @Valid @RequestBody CreateGoalRequest req,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Goal created", service.create(req, userId)));
    }

    @GetMapping("/goals/{id}")
    public ResponseEntity<ApiResponse<GoalResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        GoalResponse g = service.get(id);
        if (g.getProjectId() != null) access.requireCanViewProject(userId, g.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(g));
    }

    @GetMapping("/projects/{projectId}/goals")
    public ResponseEntity<ApiResponse<List<GoalResponse>>> listByProject(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.listByProject(projectId)));
    }

    // ✅ PHASE 4 (F-P17): company / quarter goals are SA-only (matches create/update/delete).
    @GetMapping("/goals")
    public ResponseEntity<ApiResponse<List<GoalResponse>>> listByQuarter(
            @RequestParam(required = false) String quarter,
            @RequestParam(required = false) Integer year,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok(
                service.listByQuarter(quarter != null ? quarter : "Q3",
                        year != null ? year : 2026)));
    }

    @PutMapping("/goals/{id}")
    public ResponseEntity<ApiResponse<GoalResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CreateGoalRequest req,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req, userId)));
    }

    @DeleteMapping("/goals/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @PostMapping("/goals/{id}/key-results")
    public ResponseEntity<ApiResponse<KeyResultResponse>> addKr(
            @PathVariable Long id,
            @Valid @RequestBody CreateKeyResultRequest req,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Key result added", service.addKeyResult(id, req, userId)));
    }

    @PutMapping("/key-results/{krId}")
    public ResponseEntity<ApiResponse<KeyResultResponse>> updateKr(
            @PathVariable Long krId,
            @Valid @RequestBody CreateKeyResultRequest req,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok(service.updateKeyResult(krId, req, userId)));
    }

    @DeleteMapping("/key-results/{krId}")
    public ResponseEntity<ApiResponse<Void>> deleteKr(
            @PathVariable Long krId, @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        service.deleteKeyResult(krId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}