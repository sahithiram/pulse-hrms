package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.InterviewService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 *   POST /api/interviews                              {applicationId, round?, interviewers[], scheduledAt, mode?, linkLocation?}
 *   POST /api/interviews/{id}/status                  {status: COMPLETED|CANCELLED|NO_SHOW}
 *   POST /api/interviews/{id}/feedback                {recommendation, competencyRatings?, notes?}
 *   GET  /api/interviews/{id}/feedback                visibility-filtered (own until submitted; own+others after; HR all)
 *   GET  /api/interviews/by-application/{applicationId}
 */
@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    @Autowired private InterviewService interviewService;

    @GetMapping("/by-application/{applicationId}")
    public ResponseEntity<?> byApplication(@PathVariable Long applicationId, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(interviewService.interviewsFor(applicationId));
    }

    @PostMapping
    public ResponseEntity<?> schedule(@RequestBody Map<String, Object> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            Long applicationId = body.get("applicationId") instanceof Number n ? n.longValue() : null;
            int round = body.get("round") instanceof Number n ? n.intValue() : 1;
            List<String> interviewers = body.get("interviewers") instanceof List<?> l
                    ? l.stream().map(String::valueOf).toList() : List.of();
            LocalDateTime when = body.get("scheduledAt") == null ? null : LocalDateTime.parse(String.valueOf(body.get("scheduledAt")));
            var interview = interviewService.schedule(applicationId, round, interviewers, when,
                    str(body.get("mode")), str(body.get("linkLocation")));
            return ResponseEntity.ok(interview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            return ResponseEntity.ok(interviewService.markStatus(id, body == null ? null : body.get("status")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/feedback")
    public ResponseEntity<?> feedback(@PathVariable Long id, @RequestBody Map<String, String> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            var fb = interviewService.recordFeedback(id, u,
                    body == null ? null : body.get("competencyRatings"),
                    body == null ? null : body.get("recommendation"),
                    body == null ? null : body.get("notes"));
            return ResponseEntity.ok(Map.of("status", "RECORDED", "feedbackId", fb.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/feedback")
    public ResponseEntity<?> visibleFeedback(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean hr,
                                             HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(Map.of("interviewId", id, "feedback", interviewService.visibleFeedback(id, u, hr)));
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
