package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.WorkloadResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController("pmWorkloadController")
@RequestMapping("/api/pm/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService service;
    private final PmAccessService access;

    private void requireTeamViewer(Long userId, Long teamId) {
        if (!access.isAdmin(userId) && !access.isTeamLead(userId, teamId))
            throw new PmAccessDeniedException("Only admins or the team lead can view this workload.");
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WorkloadResponse>> me(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.ok(service.forEmployee(userId, from, to)));
    }

    @GetMapping("/employee/{employeePk}")
    public ResponseEntity<ApiResponse<WorkloadResponse>> employee(
            @PathVariable Long employeePk,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!access.canViewEmployeeTimesheet(userId, employeePk))
            throw new PmAccessDeniedException("You can't view this person's workload.");
        return ResponseEntity.ok(ApiResponse.ok(service.forEmployee(employeePk, from, to)));
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<ApiResponse<List<WorkloadResponse>>> team(
            @PathVariable Long teamId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireTeamViewer(userId, teamId);
        return ResponseEntity.ok(ApiResponse.ok(service.forTeam(teamId, from, to)));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<WorkloadResponse>>> project(
            @PathVariable Long projectId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.requireCanManageProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(service.forProject(projectId, from, to)));
    }

    @GetMapping("/team/{teamId}/warnings")
    public ResponseEntity<ApiResponse<List<String>>> warnings(
            @PathVariable Long teamId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireTeamViewer(userId, teamId);
        List<String> warnings = service.forTeam(teamId, from, to).stream()
                .filter(w -> "OVER".equals(w.getStatus()) || w.getLeaveDays() > 0)
                .map(w -> "OVER".equals(w.getStatus())
                        ? w.getEmployeeName() + " is overallocated at " + w.getUtilizationPct() + "%"
                        : w.getEmployeeName() + " has " + w.getLeaveDays() + " day(s) leave")
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(warnings));
    }
}