package com.mentis.hrms.controller;

import com.mentis.hrms.model.LeaveCancellationRequest;
import com.mentis.hrms.service.LeaveCancellationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WAS A 0-BYTE STUB — now implemented.
 *   POST /api/leave/cancellation/request          {leaveRequestId, reason, partialDays?: ["2026-12-01", ...]}
 *   GET  /api/leave/cancellation/mine
 *   GET  /api/leave/cancellation/pending          (manager/HR)
 *   POST /api/leave/cancellation/{id}/approve     {remarks?}
 *   POST /api/leave/cancellation/{id}/reject      {remarks?}
 * Authorization: Sprint-2 interceptor handles coarse auth; approve/reject SHOULD also be
 * manager/HR-checked against the leave's approver chain (see 02_LEAVE_API.md).
 */
@RestController
@RequestMapping("/api/leave/cancellation")
public class LeaveCancellationController {

    @Autowired
    private LeaveCancellationService cancellationService;

    @PostMapping("/request")
    public ResponseEntity<?> request(@RequestBody Map<String, Object> body, HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String leaveRequestId = String.valueOf(body.get("leaveRequestId"));
        String reason = body.get("reason") == null ? null : body.get("reason").toString();
        List<LocalDate> partial = null;
        Object pd = body.get("partialDays");
        if (pd instanceof List<?> list) {
            partial = list.stream().map(o -> LocalDate.parse(o.toString())).collect(Collectors.toList());
        }
        try {
            LeaveCancellationRequest cr = cancellationService.requestCancellation(leaveRequestId, employeeId, reason, partial);
            return ResponseEntity.ok(toJson(cr));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(cancellationService.myRequests(employeeId).stream().map(this::toJson).collect(Collectors.toList()));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> pending(HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(cancellationService.pending().stream().map(this::toJson).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                     HttpSession session) {
        String approver = sessionEmployee(session);
        if (approver == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String remarks = body != null && body.get("remarks") != null ? body.get("remarks").toString() : null;
        try {
            return ResponseEntity.ok(toJson(cancellationService.approve(id, approver, remarks)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                    HttpSession session) {
        String approver = sessionEmployee(session);
        if (approver == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String remarks = body != null && body.get("remarks") != null ? body.get("remarks").toString() : null;
        try {
            return ResponseEntity.ok(toJson(cancellationService.reject(id, approver, remarks)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toJson(LeaveCancellationRequest cr) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cr.getId());
        m.put("leaveRequestId", cr.getLeaveRequestId());
        m.put("requestedBy", cr.getRequestedBy());
        m.put("reason", cr.getReason());
        m.put("status", cr.getStatus());
        m.put("partialDays", cr.getPartialDays());
        m.put("approverId", cr.getApproverId());
        m.put("actedAt", cr.getActedAt() == null ? null : cr.getActedAt().toString());
        return m;
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
