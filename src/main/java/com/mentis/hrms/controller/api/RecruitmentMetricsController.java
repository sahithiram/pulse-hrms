package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.RecruitmentMetricsService;
import com.mentis.hrms.service.CandidateDedupeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 *   GET  /api/recruitment/metrics
 *   GET  /api/recruitment/dedupe?email=&phone=&resumeHash=
 */
@RestController
@RequestMapping("/api/recruitment")
public class RecruitmentMetricsController {

    @Autowired private RecruitmentMetricsService metricsService;
    @Autowired private CandidateDedupeService dedupeService;

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics(HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(metricsService.all());
    }

    @GetMapping("/dedupe")
    public ResponseEntity<?> dedupe(@RequestParam(required = false) String email,
                                    @RequestParam(required = false) String phone,
                                    @RequestParam(required = false) String resumeHash,
                                    HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(dedupeService.findDuplicates(email, phone, resumeHash));
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
