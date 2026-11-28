package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.PmCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * PM-041 — holidays, leave and real availability inside PM, read straight from the HR tables.
 * Nothing here edits HR data: leave is applied for and approved in the HR module; PM only reads it.
 */
@RestController("pmCalendarController")
@RequestMapping("/api/pm/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final PmCalendarService calendar;
    private final PmAccessService access;

    /** Company holidays in a window (everyone in PM may see these). */
    @GetMapping("/holidays")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> holidays(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (userId == null) throw new com.mentis.hrms.pm.exception.PmAccessDeniedException("Sign in to view holidays.");
        return ResponseEntity.ok(ApiResponse.ok(calendar.holidays(from, to)));
    }

    /** "Is today a working day?" — for the Home page. */
    @GetMapping("/today")
    public ResponseEntity<ApiResponse<Map<String, Object>>> today(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(calendar.today()));
    }

    /** My own non-working days (holidays, weekly offs, approved leave). */
    @GetMapping("/my-unavailable")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myUnavailable(
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate a = from == null ? LocalDate.now() : from;
        LocalDate b = to == null ? a.plusDays(60) : to;
        return ResponseEntity.ok(ApiResponse.ok(calendar.unavailableDays(userId, a, b)));
    }

    /** Who on this project is really available in the window, and how much capacity is left. */
    @GetMapping("/projects/{projectId}/availability")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> availability(
            @PathVariable Long projectId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.requireCanViewProjectPeople(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(calendar.projectAvailability(projectId, from, to)));
    }

    /** Leave of project people that is still waiting for HR approval. */
    @GetMapping("/projects/{projectId}/pending-leave")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pendingLeave(
            @PathVariable Long projectId,
            @CurrentUserId Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        access.requireCanViewProjectPeople(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(calendar.pendingLeave(projectId, from, to)));
    }
}
