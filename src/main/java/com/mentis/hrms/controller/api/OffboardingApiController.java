package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.ExitService;
import com.mentis.hrms.service.LifecycleMetricsService;
import com.mentis.hrms.service.ResignationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** Sprint 8 lifecycle API — resignation, exit checklist, exit interview, encashment, metrics. */
@RestController
@RequestMapping("/api/offboarding")
public class OffboardingApiController {

    @Autowired private ResignationService resignationService;
    @Autowired private ExitService exitService;
    @Autowired private LifecycleMetricsService metricsService;

    @PostMapping("/resignation/submit")
    public ResponseEntity<?> submit(@RequestParam String employeeId,
                                    @RequestParam String requestedLastDay,
                                    @RequestParam(required = false) String reason,
                                    HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        try {
            return ResponseEntity.ok(Map.of("success", true, "resignation",
                    resignationService.submit(employeeId, LocalDate.parse(requestedLastDay), reason,
                            OnboardingApiController.actor(session))));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return OnboardingApiController.badRequest(e.getMessage());
        }
    }

    @PostMapping("/resignation/{resignationId}/accept")
    public ResponseEntity<?> accept(@PathVariable Long resignationId,
                                    @RequestParam(required = false) String acceptedLastDay,
                                    @RequestParam(required = false, defaultValue = "false") boolean noticeBuyout,
                                    HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden(); // HR accepts
        try {
            LocalDate day = acceptedLastDay == null ? null : LocalDate.parse(acceptedLastDay);
            return ResponseEntity.ok(Map.of("success", true, "resignation",
                    resignationService.accept(resignationId, day, noticeBuyout, OnboardingApiController.actor(session))));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return OnboardingApiController.badRequest(e.getMessage());
        }
    }

    @PostMapping("/resignation/{resignationId}/revoke")
    public ResponseEntity<?> revoke(@PathVariable Long resignationId,
                                    @RequestParam(required = false) String reason,
                                    HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        try {
            return ResponseEntity.ok(Map.of("success", true, "resignation",
                    resignationService.revoke(resignationId, reason, OnboardingApiController.actor(session))));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return OnboardingApiController.badRequest(e.getMessage());
        }
    }

    @GetMapping("/resignations")
    public ResponseEntity<?> recent(HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true, "resignations", resignationService.recent()));
    }

    @PostMapping("/resignations/run-completion")
    public ResponseEntity<?> runCompletion(HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true, "finalized", resignationService.completeOverdueExits()));
    }

    @GetMapping("/exit/checklist")
    public ResponseEntity<?> checklist(@RequestParam String employeeId, HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.manager(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true, "checklist", exitService.checklist(employeeId)));
    }

    @PostMapping("/exit/checklist/{task}/complete")
    public ResponseEntity<?> completeTask(@PathVariable String task,
                                          @RequestParam String employeeId,
                                          @RequestParam(required = false, defaultValue = "DONE") String status,
                                          @RequestParam(required = false) String notes,
                                          HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.manager(session)) return OnboardingApiController.forbidden();
        try {
            return ResponseEntity.ok(Map.of("success", true, "task",
                    exitService.markTask(employeeId, task, status, OnboardingApiController.actor(session), notes)));
        } catch (IllegalArgumentException e) {
            return OnboardingApiController.badRequest(e.getMessage());
        }
    }

    @PostMapping("/exit/interview")
    public ResponseEntity<?> interview(@RequestParam String employeeId,
                                       @RequestParam String formJson,
                                       HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        return ResponseEntity.ok(Map.of("success", true,
                "interview", exitService.submitExitInterview(employeeId, formJson, OnboardingApiController.actor(session))));
    }

    @GetMapping("/exit/encashment")
    public ResponseEntity<?> encashment(@RequestParam String employeeId, HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden(); // payroll-relevant
        return ResponseEntity.ok(Map.of("success", true, "encashment", exitService.encashmentReport(employeeId)));
    }

    @PostMapping("/exit/finalize")
    public ResponseEntity<?> finalizeExit(@RequestParam String employeeId, HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true,
                "result", exitService.finalizeExit(employeeId, "RESIGNATION", OnboardingApiController.actor(session))));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics(HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.manager(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true, "metrics", metricsService.metrics()));
    }
}
