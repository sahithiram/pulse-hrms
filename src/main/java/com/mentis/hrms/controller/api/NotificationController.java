package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.HrmsNotification;
import com.mentis.hrms.repository.HrmsNotificationRepository;
import com.mentis.hrms.service.HrmsNotificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GET  /api/notifications/unread-count
 * GET  /api/notifications/recent?size=20 (page 0)
 * POST /api/notifications/{id}/read
 * POST /api/notifications/read-all
 *
 * NOTE: the bean name is set explicitly because com.mentis.hrms.controller.NotificationController
 * (mapped at /notifications/api) would otherwise get the same default bean name "notificationController"
 * and Spring fails at startup with ConflictingBeanDefinitionException.
 */
@RestController("apiNotificationController")   // FIX: was @RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired private HrmsNotificationService notificationService;
    @Autowired private HrmsNotificationRepository notificationRepository;

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(Map.of("unread", notificationService.unreadCount(u)));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> recent(@RequestParam(defaultValue = "20") int size, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        int capped = Math.min(Math.max(size, 1), 100);
        List<Map<String, Object>> rows = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(u, PageRequest.of(0, capped))
                .getContent().stream().map(this::toJson).collect(Collectors.toList());
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        boolean ok = notificationService.markRead(id, u);
        return ok ? ResponseEntity.ok(Map.of("status", "READ"))
                : ResponseEntity.status(404).body(Map.of("error", "NOT_FOUND"));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> readAll(HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(Map.of("marked", notificationService.markAllRead(u)));
    }

    private Map<String, Object> toJson(HrmsNotification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("eventType", n.getEventType());
        m.put("title", n.getTitle());
        m.put("body", n.getBody());
        m.put("deepLink", n.getDeepLink());
        m.put("channel", n.getChannel());
        m.put("status", n.getStatus());
        m.put("createdAt", n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
        return m;
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}