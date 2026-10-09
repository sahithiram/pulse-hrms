package com.mentis.hrms.controller;

// Add this import at the top
import com.mentis.hrms.service.LeaveBalanceService;
import com.mentis.hrms.model.Attendance;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.repository.AttendanceRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import com.mentis.hrms.service.AttendanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leaves")
public class LeaveManagementController {

    private static final Logger logger = LoggerFactory.getLogger(LeaveManagementController.class);

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private AttendanceService attendanceService;

    // ========== FIXED: Use String leaveId to match your ID format ==========
    // Add this field
    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private com.mentis.hrms.service.LeaveActionService leaveActionService;

    @GetMapping("/pending")
    public ResponseEntity<?> getPendingLeaves() {
        List<LeaveRequest> pending = leaveRequestRepository.findByStatus("PENDING");
        return ResponseEntity.ok(Map.of(
                "success", true,
                "leaves", pending,
                "count", pending.size()
        ));
    }

    @PostMapping("/{leaveId}/approve")
    public ResponseEntity<?> approveLeave(@PathVariable String leaveId,
                                          @RequestBody(required = false) Map<String, String> requestBody,
                                          Principal principal) {
        logger.info("Approving leave: {}", leaveId);

        String remarks = (requestBody != null) ? requestBody.get("remarks") : null;
        String approverId = (principal != null) ? principal.getName() : "SYSTEM";

        // Delegates to LeaveActionService, which is the single place that now owns
        // approve/reject/withdraw/cancel/apply logic (also used by the HR and
        // employee chat assistants). This endpoint's URL, params and response
        // shape are unchanged, so the existing UI keeps working as-is.
        Map<String, Object> result = leaveActionService.approveLeave(leaveId, approverId, remarks);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    // ========== SINGLE getAllLeaves METHOD - handles all cases ==========
    @GetMapping("/all")
    public ResponseEntity<?> getAllLeaves(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String employeeId) {

        try {
            List<LeaveRequest> leaves;
            List<Map<String, Object>> enrichedLeaves = new ArrayList<>();

            if (status != null && !status.isEmpty()) {
                leaves = leaveRequestRepository.findByStatus(status.toUpperCase());
            } else if (employeeId != null && !employeeId.isEmpty()) {
                leaves = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
            } else {
                // Get ALL leaves for HR dashboard
                leaves = leaveRequestRepository.findAll();
                leaves.sort((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
            }

            // Enrich leaves with employee details
            for (LeaveRequest leave : leaves) {
                Map<String, Object> enrichedLeave = new HashMap<>();

                // Copy all leave properties
                enrichedLeave.put("id", leave.getId());
                enrichedLeave.put("employeeId", leave.getEmployeeId());
                enrichedLeave.put("startDate", leave.getStartDate());
                enrichedLeave.put("endDate", leave.getEndDate());
                enrichedLeave.put("leaveType", leave.getLeaveType());
                enrichedLeave.put("reason", leave.getReason());
                enrichedLeave.put("status", leave.getStatus());
                enrichedLeave.put("approvedBy", leave.getApprovedBy());
                enrichedLeave.put("approvedDate", leave.getApprovedDate());
                enrichedLeave.put("createdAt", leave.getCreatedAt());
                enrichedLeave.put("rejectionReason", leave.getRejectionReason());
                enrichedLeave.put("approvalRemarks", leave.getApprovalRemarks());

                // Get employee details
                Employee employee = attendanceService.findEmployeeById(leave.getEmployeeId());
                if (employee != null) {
                    enrichedLeave.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
                    enrichedLeave.put("department", employee.getDepartment());
                    enrichedLeave.put("designation", employee.getDesignation());
                } else {
                    enrichedLeave.put("employeeName", "Unknown");
                    enrichedLeave.put("department", "N/A");
                    enrichedLeave.put("designation", "N/A");
                }

                enrichedLeaves.add(enrichedLeave);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "leaves", enrichedLeaves,
                    "count", enrichedLeaves.size()
            ));

        } catch (Exception e) {
            logger.error("Error fetching leaves: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/{leaveId}/reject")
    public ResponseEntity<?> rejectLeave(@PathVariable String leaveId,
                                         @RequestBody(required = false) Map<String, String> requestBody,
                                         Principal principal) {
        logger.info("Rejecting leave: {}", leaveId);

        String reason = (requestBody != null) ? requestBody.get("reason") : null;
        if (reason == null || reason.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "Rejection reason is required"
            ));
        }
        String approverId = (principal != null) ? principal.getName() : "SYSTEM";

        Map<String, Object> result = leaveActionService.rejectLeave(leaveId, approverId, reason);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    @GetMapping("/debug/{leaveId}")
    public ResponseEntity<?> debugLeave(@PathVariable String leaveId) {
        try {
            LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Not found"));

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "leaveId", leave.getId(),
                    "status", leave.getStatus(),
                    "statusLength", leave.getStatus() != null ? leave.getStatus().length() : 0,
                    "approvedBy", leave.getApprovedBy(),
                    "approvedDate", leave.getApprovedDate()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @PostMapping("/apply")
    public ResponseEntity<?> applyForLeave(@RequestBody LeaveRequest leaveRequest, Principal principal) {
        logger.info("Applying for leave: {}", leaveRequest);

        Map<String, Object> result = leaveActionService.applyLeave(leaveRequest);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

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
                    "hasSufficientBalance", hasBalance,
                    "leaveType", leaveType,
                    "message", leaveType.equalsIgnoreCase("earned") ?
                            "Earned leaves are unlimited" :
                            (hasBalance ? "Sufficient balance" : "Insufficient balance")
            ));

        } catch (Exception e) {
            logger.error("Error checking balance: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @GetMapping("/check-existing")
    public ResponseEntity<?> checkExistingLeaves(
            @RequestParam String employeeId,
            @RequestParam String date) {

        try {
            LocalDate checkDate = LocalDate.parse(date);

            // Check if employee has approved or pending leave on this date
            List<LeaveRequest> existingLeaves = leaveRequestRepository
                    .findByEmployeeIdOrderByCreatedAtDesc(employeeId)
                    .stream()
                    .filter(leave -> {
                        return !checkDate.isBefore(leave.getStartDate()) &&
                                !checkDate.isAfter(leave.getEndDate()) &&
                                (leave.getStatus().equals("PENDING") ||
                                        leave.getStatus().equals("APPROVED"));
                    })
                    .collect(Collectors.toList());

            boolean hasLeave = !existingLeaves.isEmpty();
            String leaveStatus = hasLeave ? existingLeaves.get(0).getStatus() : null;
            String leaveId = hasLeave ? existingLeaves.get(0).getId() : null;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "hasLeave", hasLeave,
                    "leaveStatus", leaveStatus,
                    "leaveId", leaveId,
                    "date", date
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }


    // ==================== NEW METHODS FOR AI CHAT ASSISTANT ====================

    // NEW: Calendar data for mini calendar (holidays + existing leaves)
    @GetMapping("/calendar")
    public ResponseEntity<?> getLeaveCalendar(@RequestParam String employeeId,
                                              @RequestParam int year,
                                              @RequestParam int month) {
        try {
            Map<String, Object> data = new HashMap<>();
            List<LeaveRequest> leaves = leaveRequestRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId);

            data.put("success", true);
            data.put("holidays", new ArrayList<>()); // Add Holiday service data here if available
            data.put("leaves", leaves.stream().map(leave -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", leave.getId());
                m.put("startDate", leave.getStartDate());
                m.put("endDate", leave.getEndDate());
                m.put("status", leave.getStatus());
                m.put("type", leave.getLeaveType());
                return m;
            }).collect(Collectors.toList()));
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // NEW: Apply via Chat (used by AI Assistant)
    @PostMapping("/apply-chat")
    public ResponseEntity<?> applyLeaveViaChat(@RequestBody LeaveRequest leaveRequest, Principal principal) {
        try {
            if (leaveRequest.getEmployeeId() == null && principal != null) {
                leaveRequest.setEmployeeId(principal.getName());
            }
            // Reuse existing apply logic
            return applyForLeave(leaveRequest, principal);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // Cancel Leave: employee cancels a PENDING or already-APPROVED leave.
    // If it was APPROVED, the deducted leave balance is automatically restored.
    // (Previously this only allowed PENDING and never restored balance - fixed
    // now that it delegates to LeaveActionService.)
    @PostMapping("/{leaveId}/cancel")
    public ResponseEntity<?> cancelLeave(@PathVariable String leaveId,
                                         @RequestBody(required = false) Map<String, String> body,
                                         Principal principal) {
        String reason = (body != null) ? body.get("reason") : null;
        Map<String, Object> result = leaveActionService.cancelLeave(leaveId, reason);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    // Withdraw Leave: employee retracts a request that is still PENDING
    // (HR hasn't acted on it yet). Now correctly sets status=WITHDRAWN
    // (previously this only set the withdrawn flag and left status untouched).
    @PostMapping("/{leaveId}/withdraw")
    public ResponseEntity<?> withdrawLeave(@PathVariable String leaveId,
                                           @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null) ? body.get("reason") : null;
        Map<String, Object> result = leaveActionService.withdrawLeave(leaveId, reason);
        boolean ok = Boolean.TRUE.equals(result.get("success"));
        return ok ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
