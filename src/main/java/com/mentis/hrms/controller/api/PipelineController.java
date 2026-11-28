package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.PipelineService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 *   GET  /api/pipeline/applications/{applicationId}          current stage + history
 *   POST /api/pipeline/applications/{applicationId}/transition  {toStage, reason?}
 */
@RestController
@RequestMapping("/api/pipeline/applications")
public class PipelineController {

    @Autowired private PipelineService pipelineService;

    @GetMapping("/{applicationId}")
    public ResponseEntity<?> view(@PathVariable Long applicationId, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(Map.of(
                "applicationId", applicationId,
                "currentStage", pipelineService.currentStage(applicationId),
                "history", pipelineService.history(applicationId)));
    }

    @PostMapping("/{applicationId}/transition")
    public ResponseEntity<?> transition(@PathVariable Long applicationId, @RequestBody Map<String, String> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            var h = pipelineService.transition(applicationId,
                    body == null ? null : body.get("toStage"),
                    u,
                    body == null ? null : body.get("reason"));
            return ResponseEntity.ok(Map.of("status", "TRANSITIONED", "toStage", h.getToStage(), "fromStage", h.getFromStage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
