package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.Requisition;
import com.mentis.hrms.service.RequisitionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 *   GET  /api/requisitions?status=OPEN
 *   POST /api/requisitions                      (create draft)
 *   POST /api/requisitions/{id}/submit|approve|reject|hold|resume|close
 *   POST /api/requisitions/{id}/link-job        {jobId}
 */
@RestController
@RequestMapping("/api/requisitions")
public class RequisitionController {

    @Autowired private RequisitionService requisitionService;

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(Requisition.OPEN.equals(status) || "ON_HOLD".equals(status) || "PENDING_APPROVAL".equals(status)
                ? requisitionService.open()
                : requisitionService.recent());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        Requisition draft = new Requisition();
        draft.setTitle(str(body.get("title")));
        draft.setDepartment(str(body.get("department")));
        draft.setLocation(str(body.get("location")));
        draft.setHeadcount(body.get("headcount") instanceof Number n ? n.intValue() : 1);
        draft.setBudgetMin(body.get("budgetMin") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null);
        draft.setBudgetMax(body.get("budgetMax") instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null);
        draft.setCurrency(str(body.get("currency")));
        draft.setHiringManager(str(body.get("hiringManager")));
        try {
            return ResponseEntity.ok(requisitionService.create(draft, u));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submit(@PathVariable Long id, HttpSession session) {
        return act(id, session, r -> requisitionService.submitForApproval(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        String u = sessionEmployee(session);
        String approver = body != null && body.get("approver") != null ? body.get("approver") : u;
        return act(id, session, r -> requisitionService.approve(id, approver));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        String reason = body == null ? null : body.get("reason");
        return act(id, session, r -> requisitionService.sendBackToDraft(id, reason));
    }

    @PostMapping("/{id}/hold")
    public ResponseEntity<?> hold(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        return act(id, session, r -> requisitionService.placeOnHold(id, body == null ? null : body.get("reason")));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<?> resume(@PathVariable Long id, HttpSession session) {
        return act(id, session, r -> requisitionService.resume(id));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<?> close(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body, HttpSession session) {
        return act(id, session, r -> requisitionService.close(id, body == null ? null : body.get("reason")));
    }

    @PostMapping("/{id}/link-job")
    public ResponseEntity<?> linkJob(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        Object jobIdRaw = body == null ? null : body.get("jobId");
        if (!(jobIdRaw instanceof Number n)) return ResponseEntity.status(400).body(Map.of("error", "jobId (number) required"));
        try {
            return ResponseEntity.ok(requisitionService.linkJob(id, n.longValue()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    private ResponseEntity<?> act(Long id, HttpSession session, java.util.function.Function<Void, Requisition> fn) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            return ResponseEntity.ok(fn.apply(null));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        }
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
