package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateRiskRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.RiskResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.RiskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("pmRiskController")
@RequestMapping("/api/pm/risks")
@RequiredArgsConstructor
public class RiskController {

    private final RiskService service;
    private final PmAccessService access;

    private Long projectOf(Long riskId) {
        return service.get(riskId).getProjectId();
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RiskResponse>> create(
            @Valid @RequestBody CreateRiskRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, req.getProjectId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Risk created", service.create(req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RiskResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        RiskResponse r = service.get(id);
        access.requireCanViewProject(userId, r.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(r));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<RiskResponse>>> listByProject(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.listByProject(projectId)));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<ApiResponse<RiskResponse>> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, body.get("status"), userId)));
    }

    @PostMapping("/{id}/mitigation")
    public ResponseEntity<ApiResponse<RiskResponse>> updateMitigation(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        return ResponseEntity.ok(ApiResponse.ok(service.updateMitigation(id, body.get("mitigation"), userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, projectOf(id));
        service.delete(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}