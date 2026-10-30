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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("pmWorkloadController")
@RequestMapping("/api/pm/workload")
@RequiredArgsConstructor
public class WorkloadController {

    private final WorkloadService service;
    private final PmAccessService access;

    private void requireTeamViewer(Long userId, Long teamId) {
        if (!access.isAdmin(userId) && !access.isPmRole(userId) && !access.isTeamLead(userId, teamId))
            throw new PmAccessDeniedException("Only admins, project managers or the team lead can view this workload.");
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
        access.requireCanViewProjectPeople(userId, projectId); // STEP 6
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

    /**
     * STEP 6: company-wide utilization for HR and admins (people view, no money).
     * Optional ?teamId= narrows it to one team. Returns { summary, people }.
     */
    @GetMapping("/utilization")
    public ResponseEntity<ApiResponse<Map<String, Object>>> utilization(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (!access.isAdmin(userId))
            throw new PmAccessDeniedException("Only HR and admins can view company-wide utilization.");

        List<WorkloadResponse> people = teamId != null
                ? service.forTeam(teamId, from, to)
                : service.forEveryone(from, to);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("people", people.size());
        summary.put("avgUtilizationPct", Math.round(people.stream()
                .mapToInt(WorkloadResponse::getUtilizationPct).average().orElse(0)));
        summary.put("over", people.stream().filter(p -> "OVER".equals(p.getStatus())).count());
        summary.put("warn", people.stream().filter(p -> "WARN".equals(p.getStatus())).count());
        summary.put("ok", people.stream().filter(p -> "OK".equals(p.getStatus())).count());
        summary.put("under", people.stream().filter(p -> "UNDER".equals(p.getStatus())).count());
        summary.put("onLeave", people.stream().filter(p -> p.getLeaveDays() > 0).count());
        summary.put("capacityHours", round1(people.stream().mapToDouble(WorkloadResponse::getCapacityHours).sum()));
        summary.put("allocatedHours", round1(people.stream().mapToDouble(WorkloadResponse::getAllocatedHours).sum()));
        summary.put("loggedHours", round1(people.stream().mapToDouble(WorkloadResponse::getLoggedHours).sum()));
        summary.put("leaveHours", round1(people.stream().mapToDouble(WorkloadResponse::getLeaveHours).sum()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("summary", summary);
        body.put("people", people);
        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    private double round1(double x) { return Math.round(x * 10.0) / 10.0; }
}