package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ReportDtos.*;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController("pmReportController")
@RequestMapping("/api/pm/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;
    private final PmAccessService access;

    /** no projectId = all projects = admin only; otherwise must be able to view that project */
    private void scopeView(Long userId, Long projectId) {
        if (projectId == null) access.requireAdmin(userId);
        else access.requireCanViewProject(userId, projectId);
    }

    /**
     * STEP 1b: budget is money data. All-projects budget = Super Admin only;
     * a single project's budget = owner / MANAGER / Super Admin (finance tier).
     * HR and the Lead tier no longer qualify.
     */
    private void scopeFinance(Long userId, Long projectId) {
        if (projectId == null) access.requireSuperAdmin(userId);
        else access.requireCanViewFinance(userId, projectId);
    }

    @GetMapping("/budget")
    public ResponseEntity<ApiResponse<List<BudgetRow>>> budget(
            @RequestParam(required = false) Long projectId, @CurrentUserId Long userId) {
        scopeFinance(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.budget(projectId)));
    }

    @GetMapping("/velocity")
    public ResponseEntity<ApiResponse<List<VelocityRow>>> velocity(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "6") int lastSprints,
            @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.velocity(projectId, lastSprints)));
    }

    @GetMapping("/quality")
    public ResponseEntity<ApiResponse<QualityReport>> quality(
            @RequestParam(required = false) Long projectId, @CurrentUserId Long userId) {
        scopeView(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.quality(projectId)));
    }

    @GetMapping("/risk")
    public ResponseEntity<ApiResponse<RiskReport>> risk(
            @RequestParam(required = false) Long projectId, @CurrentUserId Long userId) {
        scopeView(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.risk(projectId)));
    }

    @GetMapping("/utilization")
    public ResponseEntity<ApiResponse<List<UtilizationRow>>> utilization(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.requireAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok(service.utilization(from, to)));
    }

    // ── CSV exports ──
    @GetMapping(value = "/budget/export.csv", produces = "text/csv")
    public ResponseEntity<String> budgetCsv(@RequestParam(required = false) Long projectId,
                                            @CurrentUserId Long userId) {
        scopeFinance(userId, projectId);
        return csv(service.budgetCsv(projectId), "budget");
    }

    @GetMapping(value = "/velocity/export.csv", produces = "text/csv")
    public ResponseEntity<String> velocityCsv(@RequestParam Long projectId,
                                              @RequestParam(defaultValue = "6") int lastSprints,
                                              @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return csv(service.velocityCsv(projectId, lastSprints), "velocity");
    }

    @GetMapping(value = "/quality/export.csv", produces = "text/csv")
    public ResponseEntity<String> qualityCsv(@RequestParam(required = false) Long projectId,
                                             @CurrentUserId Long userId) {
        scopeView(userId, projectId);
        return csv(service.qualityCsv(projectId), "quality");
    }

    @GetMapping(value = "/risk/export.csv", produces = "text/csv")
    public ResponseEntity<String> riskCsv(@RequestParam(required = false) Long projectId,
                                          @CurrentUserId Long userId) {
        scopeView(userId, projectId);
        return csv(service.riskCsv(projectId), "risk");
    }

    @GetMapping(value = "/utilization/export.csv", produces = "text/csv")
    public ResponseEntity<String> utilizationCsv(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.requireAdmin(userId);
        return csv(service.utilizationCsv(from, to), "utilization");
    }

    private ResponseEntity<String> csv(String body, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + "-" + LocalDate.now() + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(body);
    }
}