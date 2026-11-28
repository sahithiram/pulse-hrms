package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.OnboardingTask;
import com.mentis.hrms.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Sprint 8 lifecycle API — onboarding tasks, completion rule, feedback.
 * Session guard: userId present or 401. HR-only actions check role HR/SUPER_ADMIN/ADMIN.
 * No endpoints here are public — RoleInterceptorV2 default-deny applies upstream.
 */
@RestController
@RequestMapping("/api/onboarding")
public class OnboardingApiController {

    @Autowired private OnboardingTemplateService templateService;
    @Autowired private OnboardingTaskService taskService;
    @Autowired private OnboardingLifecycleService lifecycleService;
    @Autowired private DocumentVerificationService documentService;

    @GetMapping("/tasks")
    public ResponseEntity<?> tasks(@RequestParam String employeeId, HttpSession session) {
        if (!authed(session)) return unauthorized();
        List<OnboardingTask> tasks = templateService.tasks(employeeId);
        return ResponseEntity.ok(Map.of("success", true, "tasks", tasks));
    }

    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<?> complete(@PathVariable Long taskId,
                                      @RequestParam(required = false) String evidence,
                                      HttpSession session) {
        if (!authed(session)) return unauthorized();
        try {
            OnboardingTask task = taskService.complete(taskId, actor(session), evidence);
            return ResponseEntity.ok(Map.of("success", true, "task", task));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/tasks/{taskId}/skip")
    public ResponseEntity<?> skip(@PathVariable Long taskId,
                                  @RequestParam(required = false) String reason,
                                  HttpSession session) {
        if (!authed(session)) return unauthorized();
        try {
            OnboardingTask task = taskService.skip(taskId, actor(session), reason);
            return ResponseEntity.ok(Map.of("success", true, "task", task));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/tasks/seed-templates")
    public ResponseEntity<?> seedTemplates(HttpSession session) {
        if (!authed(session)) return unauthorized();
        if (!hr(session)) return forbidden();
        templateService.seedDefaultTemplate(actor(session));
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/tasks/instantiate")
    public ResponseEntity<?> instantiate(@RequestParam String employeeId,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(required = false) String designation,
                                         @RequestParam(required = false) String startDate,
                                         HttpSession session) {
        if (!authed(session)) return unauthorized();
        if (!hr(session)) return forbidden();
        try {
            LocalDate start = startDate == null ? null : LocalDate.parse(startDate);
            List<OnboardingTask> tasks = templateService.instantiateForEmployee(employeeId, department, designation, start);
            return ResponseEntity.ok(Map.of("success", true, "created", tasks.size()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/completion")
    public ResponseEntity<?> completionReport(@RequestParam String employeeId, HttpSession session) {
        if (!authed(session)) return unauthorized();
        return ResponseEntity.ok(Map.of("success", true, "report", lifecycleService.completionReport(employeeId)));
    }

    @PostMapping("/completion/promote")
    public ResponseEntity<?> promote(@RequestParam String employeeId, HttpSession session) {
        if (!authed(session)) return unauthorized();
        if (!hr(session) && !manager(session)) return forbidden();
        try {
            return ResponseEntity.ok(Map.of("success", true, "result", lifecycleService.maybeComplete(employeeId, actor(session))));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/completion/override")
    public ResponseEntity<?> override(@RequestParam String employeeId,
                                      @RequestParam String reason,
                                      HttpSession session) {
        if (!authed(session)) return unauthorized();
        if (!hr(session)) return forbidden(); // overrides are an HR-only power (REQ-8-03)
        try {
            return ResponseEntity.ok(Map.of("success", true, "result", lifecycleService.overrideComplete(employeeId, reason, actor(session))));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/document/{documentId}/expiry")
    public ResponseEntity<?> setExpiry(@PathVariable Long documentId,
                                       @RequestParam String expiresOn,
                                       HttpSession session) {
        if (!authed(session)) return unauthorized();
        if (!hr(session)) return forbidden();
        try {
            return ResponseEntity.ok(Map.of("success", true,
                    "meta", documentService.setExpiry(documentId, LocalDate.parse(expiresOn))));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/feedback")
    public ResponseEntity<?> feedback(@RequestParam String employeeId,
                                      @RequestParam int day,
                                      @RequestParam(required = false) String ratingsJson,
                                      @RequestParam(required = false) String comments,
                                      HttpSession session) {
        if (!authed(session)) return unauthorized();
        try {
            return ResponseEntity.ok(Map.of("success", true,
                    "feedback", lifecycleService.submitFeedback(employeeId, day, ratingsJson, comments, actor(session))));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/feedback")
    public ResponseEntity<?> feedbackFor(@RequestParam String employeeId, HttpSession session) {
        if (!authed(session)) return unauthorized();
        if (!hr(session)) return forbidden(); // HR-only visibility (REQ-8-04)
        return ResponseEntity.ok(Map.of("success", true, "feedback", lifecycleService.feedbackFor(employeeId)));
    }

    /* ---------- shared session helpers (mirror Sprint 7 controller/api pattern) ---------- */
    static boolean authed(HttpSession session) {
        return session != null && session.getAttribute("userId") != null;
    }

    static String actor(HttpSession session) {
        Object u = session.getAttribute("userId");
        Object n = session.getAttribute("userName");
        return n != null ? String.valueOf(n) : String.valueOf(u);
    }

    static boolean hr(HttpSession session) {
        Object role = session.getAttribute("userRole");
        String r = role == null ? "" : role.toString().toUpperCase();
        return r.equals("HR") || r.equals("SUPER_ADMIN") || r.equals("ADMIN");
    }

    static boolean manager(HttpSession session) {
        Object role = session.getAttribute("userRole");
        String r = role == null ? "" : role.toString().toUpperCase();
        return r.equals("MANAGER") || hr(session);
    }

    static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(401).body(Map.of("success", false,
                "error", Map.of("code", "UNAUTHORIZED", "message", "Sign in required")));
    }

    static ResponseEntity<Map<String, Object>> forbidden() {
        return ResponseEntity.status(403).body(Map.of("success", false,
                "error", Map.of("code", "FORBIDDEN", "message", "HR or manager role required")));
    }

    static ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false,
                "error", Map.of("code", "BAD_REQUEST", "message", String.valueOf(message))));
    }
}
