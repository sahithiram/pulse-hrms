package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.AttendanceRegularization;
import com.mentis.hrms.service.RegularizationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 *   POST /api/attendance/regularization          {date, type, requestedIn?, requestedOut?, reason?, attachment?}
 *   GET  /api/attendance/regularization/mine
 *   GET  /api/attendance/regularization/pending  (approver queue)
 *   POST /api/attendance/regularization/{id}/approve|reject {remarks?}
 */
@RestController
@RequestMapping("/api/attendance/regularization")
public class AttendanceRegularizationController {

    @Autowired
    private RegularizationService regularizationService;

    @PostMapping
    public ResponseEntity<?> request(@RequestBody Map<String, Object> body, HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            AttendanceRegularization r = regularizationService.request(
                    employeeId,
                    LocalDate.parse(str(body.get("date"))),
                    str(body.get("type")),
                    body.get("requestedIn") == null ? null : LocalDateTime.parse(str(body.get("requestedIn"))),
                    body.get("requestedOut") == null ? null : LocalDateTime.parse(str(body.get("requestedOut"))),
                    str(body.get("reason")),
                    str(body.get("attachment")));
            return ResponseEntity.ok(toJson(r));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/mine")
    public ResponseEntity<?> mine(HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(regularizationService.mine(employeeId).stream().map(this::toJson).collect(Collectors.toList()));
    }

    @GetMapping("/pending")
    public ResponseEntity<?> pending(HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(regularizationService.pending().stream().map(this::toJson).collect(Collectors.toList()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                     HttpSession session) {
        String approver = sessionEmployee(session);
        if (approver == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String remarks = body != null ? str(body.get("remarks")) : null;
        try {
            return ResponseEntity.ok(toJson(regularizationService.approve(id, approver, remarks)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                    HttpSession session) {
        String approver = sessionEmployee(session);
        if (approver == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String remarks = body != null ? str(body.get("remarks")) : null;
        try {
            return ResponseEntity.ok(toJson(regularizationService.reject(id, approver, remarks)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> toJson(AttendanceRegularization r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("employeeId", r.getEmployeeId());
        m.put("attendanceDate", r.getAttendanceDate().toString());
        m.put("type", r.getType());
        m.put("requestedIn", r.getRequestedIn() == null ? null : r.getRequestedIn().toString());
        m.put("requestedOut", r.getRequestedOut() == null ? null : r.getRequestedOut().toString());
        m.put("reason", r.getReason());
        m.put("status", r.getStatus());
        m.put("approver", r.getApprover());
        return m;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
