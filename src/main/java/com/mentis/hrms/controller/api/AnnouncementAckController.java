package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.AnnouncementPolicy;
import com.mentis.hrms.service.AnnouncementAckService;
import com.mentis.hrms.service.AnnouncementPolicyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 *   POST /api/announcements/{id}/ack            employee acknowledges a policy announcement
 *   GET  /api/announcements/{id}/ack-count      HR: how many acked
 *   GET  /api/announcements/{id}/policy         read scheduling/ack policy
 *   PUT  /api/announcements/{id}/policy         HR: set {requiresAck: bool, publishAt: "YYYY-MM-DDTHH:mm:ss"|null}
 *   GET  /api/announcements/{id}/pending        HR: list of {audience} who have NOT acked (audience passed as ?audience=e1,e2,...)
 *   PUT  /api/announcements/{id}/targets        HR: {type: DEPARTMENT|LOCATION|ROLE|INDIVIDUAL, values: [..]}
 * Schedule publish is applied app-side via AnnouncementPolicyService.isPublished(...) — the legacy
 * read path must opt in (07_EVENT_WIRING_MIGRATION.md); we do not rewrite it here.
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementAckController {

    @Autowired private AnnouncementAckService ackService;
    @Autowired private AnnouncementPolicyService policyService;

    @PostMapping("/{id}/ack")
    public ResponseEntity<?> ack(@PathVariable Long id, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        ackService.ack(id, u);
        return ResponseEntity.ok(Map.of("status", "ACKED", "announcementId", id));
    }

    @GetMapping("/{id}/ack-count")
    public ResponseEntity<?> ackCount(@PathVariable Long id, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(Map.of("announcementId", id, "ackCount", ackService.ackCount(id)));
    }

    @GetMapping("/{id}/pending")
    public ResponseEntity<?> pending(@PathVariable Long id, @RequestParam String audience, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        List<String> audienceList = Arrays.stream(audience.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(Map.of("announcementId", id, "pending", ackService.pendingAckers(id, audienceList)));
    }

    @GetMapping("/{id}/policy")
    public ResponseEntity<?> getPolicy(@PathVariable Long id, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return policyService.policy(id)
                .map(p -> ResponseEntity.ok(Map.of("announcementId", id,
                        "requiresAck", p.isRequiresAck(),
                        "publishAt", p.getPublishAt() == null ? null : p.getPublishAt().toString())))
                .orElseGet(() -> ResponseEntity.ok(Map.of("announcementId", id, "requiresAck", false, "publishAt", null)));
    }

    @PutMapping("/{id}/policy")
    public ResponseEntity<?> setPolicy(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        boolean requiresAck = Boolean.TRUE.equals(body.get("requiresAck"));
        String publishAtRaw = body.get("publishAt") == null ? null : String.valueOf(body.get("publishAt"));
        LocalDateTime publishAt = (publishAtRaw == null || publishAtRaw.isBlank()) ? null : LocalDateTime.parse(publishAtRaw);
        AnnouncementPolicy p = policyService.setPolicy(id, requiresAck, publishAt);
        return ResponseEntity.ok(Map.of("announcementId", p.getAnnouncementId(),
                "requiresAck", p.isRequiresAck(),
                "publishAt", p.getPublishAt() == null ? null : p.getPublishAt().toString()));
    }

    @PutMapping("/{id}/targets")
    public ResponseEntity<?> setTargets(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String type = body.get("type") == null ? null : String.valueOf(body.get("type")).toUpperCase(Locale.ROOT);
        Object valuesRaw = body.get("values");
        if (type == null || !List.of("DEPARTMENT", "LOCATION", "ROLE", "INDIVIDUAL").contains(type)) {
            return ResponseEntity.status(400).body(Map.of("error", "INVALID_TYPE"));
        }
        List<String> values = valuesRaw instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        policyService.setTargets(id, type, values);
        return ResponseEntity.ok(Map.of("status", "TARGETS_SET", "announcementId", id, "type", type, "count", values.size()));
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
