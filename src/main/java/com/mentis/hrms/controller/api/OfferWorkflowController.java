package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.OfferWorkflowService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 *   GET  /api/offers/{offerLetterId}/workflow
 *   POST /api/offers/{offerLetterId}/workflow           {validUntil}          create (idempotent)
 *   POST /api/offers/{offerLetterId}/submit|approve|mark-sent|accept|decline|withdraw|revise
 *   GET  /api/offers/{offerLetterId}/revisions
 * NOTE: `accept` triggers the EMPLOYEE HAND-OFF in one transaction — see README §Acceptance.
 */
@RestController
@RequestMapping("/api/offers/{offerLetterId}")
public class OfferWorkflowController {

    @Autowired private OfferWorkflowService workflowService;

    @GetMapping("/workflow")
    public ResponseEntity<?> get(@PathVariable Long offerLetterId, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return workflowService.workflow(offerLetterId)
                .map(w -> ResponseEntity.ok((Object) w))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("error", "WORKFLOW_NOT_FOUND")));
    }

    @PostMapping("/workflow")
    public ResponseEntity<?> create(@PathVariable Long offerLetterId, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String validUntil = body == null ? null : body.get("validUntil");
        LocalDate vu = validUntil == null || validUntil.isBlank() ? null : LocalDate.parse(validUntil);
        try {
            return ResponseEntity.ok(workflowService.createForOfferLetter(offerLetterId, vu));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/submit")
    public ResponseEntity<?> submit(@PathVariable Long offerLetterId, HttpSession session) {
        return act(offerLetterId, session, () -> workflowService.submitForApproval(offerLetterId));
    }

    @PostMapping("/approve")
    public ResponseEntity<?> approve(@PathVariable Long offerLetterId, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String hrApprover = body != null && body.get("approverHr") != null ? body.get("approverHr") : u;
        return act(offerLetterId, session, () -> workflowService.approve(offerLetterId, hrApprover, body == null ? null : body.get("approverBudget")));
    }

    @PostMapping("/mark-sent")
    public ResponseEntity<?> markSent(@PathVariable Long offerLetterId, HttpSession session) {
        return act(offerLetterId, session, () -> workflowService.markSent(offerLetterId));
    }

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@PathVariable Long offerLetterId, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return act(offerLetterId, session, () -> workflowService.accept(offerLetterId, u));
    }

    @PostMapping("/decline")
    public ResponseEntity<?> decline(@PathVariable Long offerLetterId, HttpSession session) {
        return act(offerLetterId, session, () -> workflowService.decline(offerLetterId));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@PathVariable Long offerLetterId, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        return act(offerLetterId, session, () -> workflowService.withdraw(offerLetterId, body == null ? null : body.get("reason")));
    }

    @PostMapping("/revise")
    public ResponseEntity<?> revise(@PathVariable Long offerLetterId, @RequestBody Map<String, String> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String reason = body == null ? null : body.get("changeReason");
        return act(offerLetterId, session, () -> workflowService.revise(offerLetterId, reason, u));
    }

    @GetMapping("/revisions")
    public ResponseEntity<?> revisions(@PathVariable Long offerLetterId, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(workflowService.revisions(offerLetterId));
    }

    private ResponseEntity<?> act(Long offerLetterId, HttpSession session, java.util.function.Supplier<Object> fn) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            return ResponseEntity.ok(fn.get());
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
