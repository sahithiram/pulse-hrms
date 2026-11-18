package com.mentis.hrms.controller;

import com.mentis.hrms.service.LeaveBalanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/leave-balance")
public class LeaveBalanceController {

    private static final Logger logger = LoggerFactory.getLogger(LeaveBalanceController.class);

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    /**
     * Get leave balance for current logged-in employee
     */
    /**
     * Balance for current logged-in employee — CURRENT MONTH.
     * Falls back to session.userId because this app uses session auth, not Spring Security.
     */
    @GetMapping("/my-balance")
    public ResponseEntity<?> getMyLeaveBalance(Principal principal, HttpSession session) {
        try {
            String employeeId = null;
            if (principal != null && principal.getName() != null
                    && !"anonymousUser".equals(principal.getName())) {
                employeeId = principal.getName();
            }
            if (employeeId == null) {
                Object s = session.getAttribute("userId");
                if (s != null) employeeId = s.toString();
            }
            if (employeeId == null || employeeId.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Employee not authenticated"
                ));
            }

            LocalDate now = LocalDate.now();
            Map<String, Object> result = leaveBalanceService.getMonthlyBalance(
                    employeeId, now.getYear(), now.getMonthValue());
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            logger.error("Error fetching leave balance: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Get leave balance for a specific employee (HR use) - UPDATED
     */
    /**
     * Monthly balance — used by the Employee Dashboard calendar card.
     * GET /api/leave-balance/employee/EMP001/monthly?year=2026&month=11
     */
    @GetMapping("/employee/{employeeId}/monthly")
    public ResponseEntity<?> getMonthlyBalance(
            @PathVariable String employeeId,
            @RequestParam(value = "year",  required = false) Integer year,
            @RequestParam(value = "month", required = false) Integer month) {
        try {
            Map<String, Object> result =
                    leaveBalanceService.getMonthlyBalance(employeeId, year, month);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching monthly balance for {}: {}", employeeId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Backward-compatible non-monthly path — returns CURRENT month's data.
     */
    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<?> getEmployeeLeaveBalance(@PathVariable String employeeId) {
        try {
            LocalDate now = LocalDate.now();
            Map<String, Object> result = leaveBalanceService.getMonthlyBalance(
                    employeeId, now.getYear(), now.getMonthValue());
            logger.info("Leave balance response for {}: {}", employeeId, result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error fetching leave balance for {}: {}", employeeId, e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Check if employee has sufficient balance before applying
     */
    @PostMapping("/check-balance")
    public ResponseEntity<?> checkBalance(@RequestBody Map<String, Object> request) {
        try {
            String employeeId = (String) request.get("employeeId");
            String leaveType = (String) request.get("leaveType");
            String startDate = (String) request.get("startDate");
            String endDate = (String) request.get("endDate");
            String leaveDuration = (String) request.get("leaveDuration");
            String halfDayType = (String) request.get("halfDayType");

            if (employeeId == null || leaveType == null || startDate == null || endDate == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Missing required fields"
                ));
            }

            // Parse dates
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            boolean hasBalance = leaveBalanceService.hasSufficientBalance(
                    employeeId, leaveType, start, end, leaveDuration, halfDayType);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasSufficientBalance", hasBalance
            ));

        } catch (Exception e) {
            logger.error("Error checking balance: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Recalculate balance for an employee (admin/HR use)
     */
    @PostMapping("/recalculate/{employeeId}")
    public ResponseEntity<?> recalculateBalance(@PathVariable String employeeId,
                                                @RequestParam(required = false) Integer year) {
        try {
            if (year == null) {
                year = LocalDate.now().getYear();
            }

            leaveBalanceService.recalculateAllBalances(employeeId, year);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Balance recalculated successfully"
            ));

        } catch (Exception e) {
            logger.error("Error recalculating balance: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }
}