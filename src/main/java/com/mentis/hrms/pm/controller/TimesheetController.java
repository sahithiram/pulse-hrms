package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.TimesheetResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TimesheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@RestController("pmTimesheetController")
@RequestMapping("/api/pm/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private final TimesheetService service;
    private final PmAccessService access;

    private LocalDate normWeek(LocalDate d) {
        if (d == null) d = LocalDate.now();
        return d.with(DayOfWeek.MONDAY);
    }

    private void requireTeamViewer(Long userId, Long teamId) {
        if (!access.isAdmin(userId) && !access.isTeamLead(userId, teamId))
            throw new PmAccessDeniedException("Only admins or the team lead can view this timesheet.");
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<TimesheetResponse>> me(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return ResponseEntity.ok(ApiResponse.ok(service.forEmployee(userId, normWeek(week))));
    }

    @GetMapping("/employee/{employeePk}")
    public ResponseEntity<ApiResponse<TimesheetResponse>> employee(
            @PathVariable Long employeePk,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        if (!access.canViewEmployeeTimesheet(userId, employeePk))
            throw new PmAccessDeniedException("You can't view this person's timesheet.");
        return ResponseEntity.ok(ApiResponse.ok(service.forEmployee(employeePk, normWeek(week))));
    }

    @GetMapping("/team/{teamId}")
    public ResponseEntity<ApiResponse<List<TimesheetResponse>>> team(
            @PathVariable Long teamId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        requireTeamViewer(userId, teamId);
        return ResponseEntity.ok(ApiResponse.ok(service.forTeam(teamId, normWeek(week))));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<TimesheetResponse>>> project(
            @PathVariable Long projectId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        access.requireCanViewProjectPeople(userId, projectId); // STEP 6: lead tier + HR (read-only)
        return ResponseEntity.ok(ApiResponse.ok(service.forProject(projectId, normWeek(week))));
    }
    /** Employee submits a week for approval. */
    @PostMapping("/submit")
    public ResponseEntity<ApiResponse<TimesheetResponse>> submit(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        return ResponseEntity.ok(ApiResponse.ok("Timesheet submitted", service.submit(userId, normWeek(week))));
    }

    /** Admin: reopen an APPROVED / REJECTED week so it can be corrected. */
    @PostMapping("/reopen")
    public ResponseEntity<ApiResponse<TimesheetResponse>> reopen(
            @RequestParam Long employee,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
            @CurrentUserId Long userId) {
        access.requireAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok("Timesheet reopened",
                service.reopen(employee, normWeek(week), userId)));
    }
    @GetMapping(value = "/team/{teamId}/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @PathVariable Long teamId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week) {
        requireTeamViewer(userId, teamId);
        String csv = service.exportCsv(teamId, normWeek(week));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"timesheet-" + teamId + "-" + normWeek(week) + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(csv);
    }
}