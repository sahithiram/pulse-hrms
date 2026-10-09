package com.mentis.hrms.service;

import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Central place for every action that changes the state of a LeaveRequest:
 * apply, approve, reject, withdraw, cancel.
 *
 * Added so that the UI (LeaveManagementController), the HR chat assistant
 * (HrAIController) and the employee chat assistant (ChatService) all share
 * exactly the same validation and side-effects (balance deduction/restore,
 * attendance sync) instead of re-implementing it three times.
 *
 * Existing REST endpoints in LeaveManagementController keep their exact
 * same paths/behaviour - they now just delegate to this service.
 */
@Service
public class LeaveActionService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveActionService.class);

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private AttendanceService attendanceService;

    // ---------------------------------------------------------------
    // APPLY - creates a new PENDING leave request
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> applyLeave(LeaveRequest leaveRequest) {
        Map<String, Object> result = new HashMap<>();

        if (leaveRequest.getEmployeeId() == null || leaveRequest.getEmployeeId().isEmpty()) {
            result.put("success", false);
            result.put("error", "Employee ID is required");
            return result;
        }
        if (leaveRequest.getStartDate() == null || leaveRequest.getEndDate() == null) {
            result.put("success", false);
            result.put("error", "Start date and end date are required");
            return result;
        }
        if (leaveRequest.getEndDate().isBefore(leaveRequest.getStartDate())) {
            result.put("success", false);
            result.put("error", "End date cannot be before start date");
            return result;
        }
        if (leaveRequest.getLeaveType() == null || leaveRequest.getLeaveType().trim().isEmpty()) {
            result.put("success", false);
            result.put("error", "Leave type is required (sick, casual, or earned)");
            return result;
        }

        boolean hasSufficientBalance = leaveBalanceService.hasSufficientBalance(
                leaveRequest.getEmployeeId(),
                leaveRequest.getLeaveType(),
                leaveRequest.getStartDate(),
                leaveRequest.getEndDate(),
                leaveRequest.getLeaveDuration(),
                leaveRequest.getHalfDayType()
        );

        if (!hasSufficientBalance) {
            result.put("success", false);
            result.put("error", "Insufficient leave balance");
            return result;
        }

        if (leaveRequest.getTotalDays() == null || leaveRequest.getTotalDays() == 0) {
            long days = ChronoUnit.DAYS.between(leaveRequest.getStartDate(), leaveRequest.getEndDate()) + 1;
            leaveRequest.setTotalDays((double) days);
        }

        leaveRequest.setStatus("PENDING");
        leaveRequest.setCreatedAt(LocalDateTime.now());

        try {
            LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
            logger.info("Leave request {} created for employee {}", saved.getId(), saved.getEmployeeId());

            result.put("success", true);
            result.put("message", "Leave request submitted successfully");
            result.put("leaveId", saved.getId());
            result.put("status", saved.getStatus());
        } catch (Exception e) {
            logger.error("Error saving leave request: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "Failed to save to database: " + e.getMessage());
        }
        return result;
    }

    // ---------------------------------------------------------------
    // APPROVE - PENDING -> APPROVED, deducts balance, creates attendance
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> approveLeave(String leaveId, String approverId, String remarks) {
        Map<String, Object> result = new HashMap<>();
        try {
            LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave not found: " + leaveId));

            if ("APPROVED".equals(leave.getStatus())) {
                result.put("success", false);
                result.put("error", "Leave " + leaveId + " is already approved");
                return result;
            }
            if (!"PENDING".equals(leave.getStatus())) {
                result.put("success", false);
                result.put("error", "Only PENDING leaves can be approved (current status: " + leave.getStatus() + ")");
                return result;
            }

            if (leave.getTotalDays() == null || leave.getTotalDays() == 0) {
                long days = ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1;
                leave.setTotalDays((double) days);
                leave.setLeaveDuration("full_day");
            }

            leave.setStatus("APPROVED");
            leave.setApprovedBy(approverId != null ? approverId : "SYSTEM");
            leave.setApprovedDate(LocalDateTime.now());
            if (remarks != null && !remarks.trim().isEmpty()) {
                leave.setApprovalRemarks(remarks);
            }

            boolean deducted = leaveBalanceService.deductLeaveBalance(leave);
            if (!deducted) {
                logger.warn("Could not deduct leave balance for {} during approval - check balance records", leaveId);
            }

            LeaveRequest saved = leaveRequestRepository.saveAndFlush(leave);

            createAttendanceForApprovedLeave(saved);

            result.put("success", true);
            result.put("message", "Leave approved successfully");
            result.put("leaveId", leaveId);
            result.put("status", saved.getStatus());
        } catch (Exception e) {
            logger.error("Error approving leave {}: {}", leaveId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ---------------------------------------------------------------
    // REJECT - PENDING -> REJECTED (no balance change, nothing was deducted yet)
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> rejectLeave(String leaveId, String approverId, String reason) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (reason == null || reason.trim().isEmpty()) {
                reason = "No reason provided";
            }

            LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave not found: " + leaveId));

            if (!"PENDING".equals(leave.getStatus())) {
                result.put("success", false);
                result.put("error", "Only PENDING leaves can be rejected (current status: " + leave.getStatus() + ")");
                return result;
            }

            leave.setStatus("REJECTED");
            leave.setApprovedBy(approverId != null ? approverId : "SYSTEM");
            leave.setApprovedDate(LocalDateTime.now());
            leave.setRejectionReason(reason);

            LeaveRequest saved = leaveRequestRepository.saveAndFlush(leave);

            result.put("success", true);
            result.put("message", "Leave rejected successfully");
            result.put("leaveId", leaveId);
            result.put("status", saved.getStatus());
            result.put("rejectionReason", reason);
        } catch (Exception e) {
            logger.error("Error rejecting leave {}: {}", leaveId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ---------------------------------------------------------------
    // WITHDRAW - employee retracts a request that is still PENDING
    // (HR hasn't acted on it yet, so there is nothing to restore)
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> withdrawLeave(String leaveId, String reason) {
        Map<String, Object> result = new HashMap<>();
        try {
            LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave not found: " + leaveId));

            if (!"PENDING".equals(leave.getStatus())) {
                result.put("success", false);
                result.put("error", "Only PENDING leave requests can be withdrawn. This leave is "
                        + leave.getStatus() + " - use cancel instead if it has already been approved.");
                return result;
            }

            leave.setStatus("WITHDRAWN");
            leave.setWithdrawn(true);
            leave.setWithdrawnDate(LocalDateTime.now());
            leave.setCancelReason(reason);

            LeaveRequest saved = leaveRequestRepository.save(leave);

            result.put("success", true);
            result.put("message", "Leave request withdrawn successfully");
            result.put("leaveId", leaveId);
            result.put("status", saved.getStatus());
        } catch (Exception e) {
            logger.error("Error withdrawing leave {}: {}", leaveId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    // ---------------------------------------------------------------
    // CANCEL - employee cancels a PENDING or an already-APPROVED leave.
    // If it was APPROVED, restores the deducted leave balance.
    // ---------------------------------------------------------------
    @Transactional
    public Map<String, Object> cancelLeave(String leaveId, String reason) {
        Map<String, Object> result = new HashMap<>();
        try {
            LeaveRequest leave = leaveRequestRepository.findById(leaveId)
                    .orElseThrow(() -> new RuntimeException("Leave not found: " + leaveId));

            String previousStatus = leave.getStatus();

            if (!"PENDING".equals(previousStatus) && !"APPROVED".equals(previousStatus)) {
                result.put("success", false);
                result.put("error", "Only PENDING or APPROVED leaves can be cancelled (current status: " + previousStatus + ")");
                return result;
            }

            leave.setStatus("CANCELLED");
            leave.setCancelReason(reason);
            leave.setWithdrawn(true);
            leave.setWithdrawnDate(LocalDateTime.now());

            boolean balanceRestored = false;
            if ("APPROVED".equals(previousStatus)) {
                balanceRestored = leaveBalanceService.addBackLeaveBalance(leave);
                if (!balanceRestored) {
                    logger.warn("Could not restore leave balance for cancelled leave {}", leaveId);
                }
                // NOTE: this does not remove the "LEAVE" attendance placeholder rows that were
                // created when the leave was approved (see createAttendanceForApprovedLeave).
                // AttendanceService does not currently expose a delete/undo method for those
                // rows. If you want the attendance calendar to also revert automatically,
                // add a method such as AttendanceService.removeLeaveAttendance(employeeId,
                // startDate, endDate) and call it here. Until then, HR may need to manually
                // clear those days from the attendance dashboard after a cancellation.
            }

            LeaveRequest saved = leaveRequestRepository.save(leave);

            result.put("success", true);
            result.put("message", "Leave cancelled successfully"
                    + (balanceRestored ? " and leave balance restored" : ""));
            result.put("leaveId", leaveId);
            result.put("status", saved.getStatus());
        } catch (Exception e) {
            logger.error("Error cancelling leave {}: {}", leaveId, e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    private void createAttendanceForApprovedLeave(LeaveRequest leave) {
        try {
            java.time.LocalDate current = leave.getStartDate();
            while (!current.isAfter(leave.getEndDate())) {
                attendanceService.createOrUpdateLeaveAttendance(leave.getEmployeeId(), current, "LEAVE");
                current = current.plusDays(1);
            }
        } catch (Exception e) {
            logger.error("Error creating attendance for approved leave {}: {}", leave.getId(), e.getMessage());
        }
    }
}
