package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.ProbationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Sprint 8 lifecycle API — probation reviews. HR gate for final decisions (REQ-8-06). */
@RestController
@RequestMapping("/api/probation")
public class ProbationApiController {

    @Autowired private ProbationService probationService;

    @GetMapping("/reviews")
    public ResponseEntity<?> pending(HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.manager(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true, "reviews", probationService.pendingReviews()));
    }

    @PostMapping("/reviews/{reviewId}/manager-review")
    public ResponseEntity<?> managerReview(@PathVariable Long reviewId,
                                           @RequestParam int managerRating,
                                           @RequestParam(required = false) String competencyRatingsJson,
                                           @RequestParam String recommendation,
                                           @RequestParam(required = false) Integer extensionDays,
                                           @RequestParam(required = false) String comments,
                                           HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.manager(session)) return OnboardingApiController.forbidden();
        try {
            return ResponseEntity.ok(Map.of("success", true, "review", probationService.submitManagerReview(
                    reviewId, managerRating, competencyRatingsJson, recommendation, extensionDays,
                    comments, OnboardingApiController.actor(session))));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return OnboardingApiController.badRequest(e.getMessage());
        }
    }

    @PostMapping("/reviews/{reviewId}/hr-decision")
    public ResponseEntity<?> hrDecision(@PathVariable Long reviewId,
                                        @RequestParam String hrDecision,
                                        @RequestParam(required = false) String note,
                                        HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden(); // HR-only gate
        try {
            return ResponseEntity.ok(Map.of("success", true, "review", probationService.hrDecision(
                    reviewId, hrDecision, OnboardingApiController.actor(session), note)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return OnboardingApiController.badRequest(e.getMessage());
        }
    }

    @PostMapping("/reviews/run-autocreate")
    public ResponseEntity<?> runAutoCreate(HttpSession session) {
        if (!OnboardingApiController.authed(session)) return OnboardingApiController.unauthorized();
        if (!OnboardingApiController.hr(session)) return OnboardingApiController.forbidden();
        return ResponseEntity.ok(Map.of("success", true, "created", probationService.autoCreateReviews()));
    }
}
