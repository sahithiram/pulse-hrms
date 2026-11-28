package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.HrmsNotificationOutbox;
import com.mentis.hrms.repository.HrmsNotificationOutboxRepository;
import com.mentis.hrms.service.NotificationOutboxProcessor;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REQ-6-02 — HR failed-email screen API (server side). Front-end page must live behind the EXISTING
 * admin/HR area with its auth guard; see 07_EVENT_WIRING_MIGRATION.md.
 *   GET  /api/admin/outbox/failed?page=0&size=20   → recent FAILED email jobs
 *   POST /api/admin/outbox/{id}/resend             → requeue one FAILED job
 */
@RestController
@RequestMapping("/api/admin/outbox")
public class HrAdminOutboxController {

    @Autowired private HrmsNotificationOutboxRepository outboxRepository;
    @Autowired private NotificationOutboxProcessor outboxProcessor;

    @GetMapping("/failed")
    public ResponseEntity<?> failed(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        int capped = Math.min(Math.max(size, 1), 100);
        List<Map<String, Object>> rows = outboxRepository
                .findByStatusOrderByIdDesc("FAILED", PageRequest.of(Math.max(page, 0), capped))
                .stream().map(this::toJson).collect(Collectors.toList());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/resend")
    public ResponseEntity<?> resend(@PathVariable Long id, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            boolean ok = outboxProcessor.resend(id);
            return ok ? ResponseEntity.ok(Map.of("status", "REQUEUED", "id", id))
                      : ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", "NOT_FAILED", "message", e.getMessage()));
        }
    }

    private Map<String, Object> toJson(HrmsNotificationOutbox o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("eventId", o.getEventId());
        m.put("eventType", o.getEventType());
        m.put("userId", o.getUserId());
        m.put("recipient", o.getRecipient());
        m.put("subject", o.getSubject());
        m.put("attempts", o.getAttempts());
        m.put("lastError", o.getLastError());
        m.put("createdAt", o.getCreatedAt() == null ? null : o.getCreatedAt().toString());
        return m;
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
