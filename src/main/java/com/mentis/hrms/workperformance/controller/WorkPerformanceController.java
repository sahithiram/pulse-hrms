package com.mentis.hrms.workperformance.controller;

import com.mentis.hrms.workperformance.model.EmployeeClickUpMapping;
import com.mentis.hrms.workperformance.model.WorkPerformanceSnapshot;
import com.mentis.hrms.workperformance.repository.EmployeeClickUpMappingRepository;
import com.mentis.hrms.workperformance.repository.WorkPerformanceSnapshotRepository;
import com.mentis.hrms.workperformance.service.PerformanceSyncService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * NEW controller. Mapped under /dashboard/hr/performance/work so it never
 * collides with the existing AdminEmpPerformanceController's routes
 * (/dashboard/hr/performance/**). Nothing here modifies the old controller.
 */
@RestController
@RequestMapping("/dashboard/hr/performance/work/api")
public class WorkPerformanceController {

    private static final Logger logger = LoggerFactory.getLogger(WorkPerformanceController.class);

    @Autowired private EmployeeClickUpMappingRepository mappingRepository;
    @Autowired private WorkPerformanceSnapshotRepository snapshotRepository;
    @Autowired private PerformanceSyncService performanceSyncService;

    private boolean isAuthorizedHr(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        return "SUPER_ADMIN".equals(role) || "HR".equals(role);
    }

    // ===== Map an employee to their ClickUp user id =====
    @PostMapping("/mapping")
    public ResponseEntity<?> saveMapping(@RequestParam String employeeId,
                                         @RequestParam String clickupUserId,
                                         @RequestParam(required = false) String clickupUsername,
                                         HttpSession session) {
        if (!isAuthorizedHr(session)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Not authorized"));
        }
        try {
            employeeId = employeeId.trim();
            clickupUserId = clickupUserId.trim();
            if (clickupUsername != null) clickupUsername = clickupUsername.trim();

            EmployeeClickUpMapping mapping = mappingRepository.findByEmployeeId(employeeId)
                    .orElse(new EmployeeClickUpMapping(employeeId, clickupUserId));
            mapping.setClickupUserId(clickupUserId);
            mapping.setClickupUsername(clickupUsername);
            mapping.setActive(true);
            mappingRepository.save(mapping);

            return ResponseEntity.ok(Map.of("success", true, "message", "Mapping saved"));
        } catch (Exception e) {
            logger.error("Error saving ClickUp mapping: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/mapping")
    public ResponseEntity<?> getAllMappings() {
        return ResponseEntity.ok(Map.of("success", true, "mappings", mappingRepository.findAll()));
    }
    @DeleteMapping("/mapping/{id}")
    public ResponseEntity<?> deleteMapping(@PathVariable Long id, HttpSession session) {
        if (!isAuthorizedHr(session)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Not authorized"));
        }
        if (!mappingRepository.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Mapping not found"));
        }
        mappingRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Mapping deleted"));
    }
    // ===== Trigger a sync for one employee =====
    @PostMapping("/sync")
    public ResponseEntity<?> syncOne(@RequestParam String employeeId, @RequestParam String month,
                                     HttpSession session) {
        if (!isAuthorizedHr(session)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Not authorized"));
        }
        try {
            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int monthNum = Integer.parseInt(parts[1]);

            WorkPerformanceSnapshot snapshot = performanceSyncService.syncOne(employeeId, year, monthNum);
            if (snapshot == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "error", "No ClickUp mapping found for this employee"));
            }
            return ResponseEntity.ok(Map.of("success", true, "result", snapshot));
        } catch (Exception e) {
            logger.error("Error syncing employee {}: {}", employeeId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ===== Trigger a fair batch sync for every mapped employee =====
    @PostMapping("/sync-all")
    public ResponseEntity<?> syncAll(@RequestParam String month, HttpSession session) {
        if (!isAuthorizedHr(session)) {
            return ResponseEntity.status(403).body(Map.of("success", false, "error", "Not authorized"));
        }
        try {
            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int monthNum = Integer.parseInt(parts[1]);

            List<WorkPerformanceSnapshot> results = performanceSyncService.syncAll(year, monthNum);
            return ResponseEntity.ok(Map.of("success", true, "count", results.size(), "results", results));
        } catch (Exception e) {
            logger.error("Error in batch sync: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ===== View results for a month, ranked =====
    @GetMapping("/results")
    public ResponseEntity<?> getResults(@RequestParam String month) {
        String[] parts = month.split("-");
        LocalDate reviewMonth = LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
        List<WorkPerformanceSnapshot> results =
                snapshotRepository.findByReviewMonthOrderByFinalCombinedScoreDesc(reviewMonth);
        return ResponseEntity.ok(Map.of("success", true, "results", results));
    }

    // ===== Combined 4-pillar view for EVERY employee (mapped or not) =====
    // This is what performance.html should call to overlay Work Performance
    // onto the existing 3-pillar table.
    @GetMapping("/combined")
    public ResponseEntity<?> getCombined(@RequestParam String month) {
        try {
            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int monthNum = Integer.parseInt(parts[1]);
            List<com.mentis.hrms.workperformance.dto.CombinedPerformanceDTO> combined =
                    performanceSyncService.getCombinedResults(year, monthNum);
            return ResponseEntity.ok(Map.of("success", true, "results", combined));
        } catch (Exception e) {
            logger.error("Error building combined results: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
