package com.mentis.hrms.service;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.entity.Team;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.repository.TeamRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class LeaveActionService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveActionService.class);

    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private LeaveBalanceService leaveBalanceService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private NotificationEmitter notificationEmitter;

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TeamRepository teamRepository;

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

            // GAP 4 FIX: Trigger Notification safely AFTER save
            notifyProjectManagersOfLeave(saved, false);

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
    // REJECT - PENDING -> REJECTED
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
            }

            LeaveRequest saved = leaveRequestRepository.save(leave);

            if ("APPROVED".equals(previousStatus)) notifyProjectManagersOfLeave(saved, true);

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

    public Map<String, Object> checkConflicts(String employeeId, LocalDate startDate, LocalDate endDate) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("hasConflicts", false);
        List<Map<String, Object>> tasksDue = new ArrayList<>();
        List<Map<String, Object>> teammatesOnLeave = new ArrayList<>();
        List<String> messages = new ArrayList<>();

        if (employeeId == null || employeeId.isBlank() || startDate == null || endDate == null) {
            result.put("tasksDue", tasksDue);
            result.put("teammatesOnLeave", teammatesOnLeave);
            result.put("messages", messages);
            return result;
        }
        if (endDate.isBefore(startDate)) {
            LocalDate tmp = startDate;
            startDate = endDate;
            endDate = tmp;
        }

        Employee employee = employeeRepository.findByEmployeeId(employeeId).orElse(null);
        if (employee == null) {
            result.put("tasksDue", tasksDue);
            result.put("teammatesOnLeave", teammatesOnLeave);
            result.put("messages", messages);
            return result;
        }

        Set<Long> seenTasks = new HashSet<>();
        for (TaskAssignment a : taskAssignmentRepository.findByEmployeeId(employee.getId())) {
            Task t = taskRepository.findById(a.getTaskId()).orElse(null);
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate ts = t.getStartDate() != null ? t.getStartDate() : t.getDueDate();
            LocalDate te = t.getDueDate() != null ? t.getDueDate() : t.getStartDate();
            if (ts == null || te == null) continue;
            if (te.isBefore(startDate) || ts.isAfter(endDate)) continue;
            if (!seenTasks.add(t.getId())) continue;
            String projectName = t.getProjectId() != null
                    ? projectRepository.findById(t.getProjectId()).map(Project::getName).orElse("Project #" + t.getProjectId())
                    : "No project";
            Map<String, Object> row = new HashMap<>();
            row.put("taskId", t.getId());
            row.put("title", t.getTitle());
            row.put("dueDate", t.getDueDate() != null ? t.getDueDate().toString() : null);
            row.put("projectName", projectName);
            tasksDue.add(row);
            messages.add("Task \"" + t.getTitle() + "\" ("
                    + (t.getDueDate() != null ? "due " + t.getDueDate() : "no due date")
                    + ") overlaps this leave on " + projectName);
        }

        Set<String> seenTeammates = new HashSet<>();
        for (ProjectMember membership : projectMemberRepository.findByEmployeeId(employee.getId())) {
            Project project = projectRepository.findById(membership.getProjectId()).orElse(null);
            String projectName = project != null ? project.getName() : "Project #" + membership.getProjectId();
            for (ProjectMember other : projectMemberRepository.findByProjectId(membership.getProjectId())) {
                if (other.getEmployeeId().equals(employee.getId())) continue;
                Employee teammate = employeeRepository.findById(other.getEmployeeId()).orElse(null);
                if (teammate == null || teammate.getEmployeeId() == null) continue;
                List<LeaveRequest> leaves = leaveRequestRepository
                        .findApprovedLeavesForEmployeeInRange(teammate.getEmployeeId(), startDate, endDate);
                if (leaves.isEmpty()) continue;
                String key = teammate.getId() + ":" + membership.getProjectId();
                if (!seenTeammates.add(key)) continue;
                String name = ((teammate.getFirstName() == null ? "" : teammate.getFirstName()) + " "
                        + (teammate.getLastName() == null ? "" : teammate.getLastName())).trim();
                if (name.isEmpty()) name = teammate.getEmployeeId();
                LeaveRequest lr = leaves.get(0);
                Map<String, Object> row = new HashMap<>();
                row.put("employeeId", teammate.getEmployeeId());
                row.put("employeeName", name);
                row.put("projectName", projectName);
                row.put("startDate", lr.getStartDate() != null ? lr.getStartDate().toString() : null);
                row.put("endDate", lr.getEndDate() != null ? lr.getEndDate().toString() : null);
                teammatesOnLeave.add(row);
                messages.add(name + " is also on leave during this period (" + projectName + ")");
            }
        }

        boolean has = !tasksDue.isEmpty() || !teammatesOnLeave.isEmpty();
        result.put("hasConflicts", has);
        result.put("tasksDue", tasksDue);
        result.put("teammatesOnLeave", teammatesOnLeave);
        result.put("messages", messages);
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

    // ================================================================
    // GAP 4 FIX: Notify Project Managers of Leave Impact
    // ================================================================
    private void notifyProjectManagersOfLeave(LeaveRequest leave, boolean cancelled) {
        try {
            if (leave.getStartDate() == null || leave.getEndDate() == null) return;
            Employee emp = employeeRepository.findByEmployeeId(leave.getEmployeeId()).orElse(null);
            if (emp == null) return;

            // Find projects where this person has an open task whose window overlaps the leave window
            Set<Long> projectIds = new LinkedHashSet<>();
            for (TaskAssignment a : taskAssignmentRepository.findByEmployeeId(emp.getId())) {
                Task t = taskRepository.findById(a.getTaskId()).orElse(null);
                if (t == null || t.getProjectId() == null || t.getStatus() == TaskStatus.DONE) continue;
                LocalDate ts = t.getStartDate() != null ? t.getStartDate() : t.getDueDate();
                LocalDate te = t.getDueDate() != null ? t.getDueDate() : t.getStartDate();
                if (ts == null || te == null) continue;
                if (te.isBefore(leave.getStartDate()) || ts.isAfter(leave.getEndDate())) continue;
                projectIds.add(t.getProjectId());
            }
            if (projectIds.isEmpty()) return;

            // Recipients per project: owner + MANAGER/LEAD members + lead of the project's team
            Map<Long, Map<Long, String>> byOwner = new LinkedHashMap<>();
            for (Long pid : projectIds) {
                Project p = projectRepository.findById(pid).orElse(null);
                if (p == null) continue;
                Set<Long> recipients = new LinkedHashSet<>();
                if (p.getOwnerId() != null) recipients.add(p.getOwnerId());
                projectMemberRepository.findByProjectId(pid).stream()
                        .filter(m -> m.getRole() == ProjectRole.MANAGER || m.getRole() == ProjectRole.LEAD)
                        .forEach(m -> recipients.add(m.getEmployeeId()));
                if (p.getTeamId() != null) {
                    teamRepository.findById(p.getTeamId()).map(Team::getLeadEmployeeId)
                            .ifPresent(lead -> { if (lead != null) recipients.add(lead); });
                }
                recipients.remove(emp.getId());
                for (Long r : recipients) {
                    byOwner.computeIfAbsent(r, k -> new LinkedHashMap<>()).put(pid, p.getName());
                }
            }

            String name = ((emp.getFirstName() == null ? "" : emp.getFirstName()) + " "
                    + (emp.getLastName() == null ? "" : emp.getLastName())).trim();
            if (name.isEmpty()) name = emp.getEmployeeId();

            for (Map.Entry<Long, Map<Long, String>> e : byOwner.entrySet()) {
                String ownerCode = employeeRepository.findById(e.getKey())
                        .map(Employee::getEmployeeId).orElse(null);
                if (ownerCode == null) continue;
                Long firstProject = e.getValue().keySet().iterator().next();

                NotificationRequest req = NotificationRequest.builder()
                        .recipientId(ownerCode)
                        .recipientType("EMPLOYEE")
                        .type(cancelled ? "LEAVE_CANCELLED" : "LEAVE_IMPACT")
                        .action(cancelled ? "LEAVE_CANCELLED" : "LEAVE_IMPACT")
                        .title(cancelled
                                ? "Leave cancelled: " + name
                                : "Team member on leave: " + name)
                        .message(cancelled
                                ? "Employee " + name + " cancelled approved leave (" + leave.getStartDate()
                                + " to " + leave.getEndDate() + "). Capacity is restored. Please re-check task assignments on "
                                + String.join(", ", e.getValue().values()) + "."
                                : "Employee " + name + " is on approved leave from " + leave.getStartDate()
                                + " to " + leave.getEndDate() + ". Please review their open tasks ("
                                + String.join(", ", e.getValue().values()) + ").")
                        .projectId(firstProject)
                        .priority("MEDIUM")
                        .deepLink("/pm/06-tasks")
                        .build();
                notificationEmitter.emit(req);
            }
        } catch (Exception ex) {
            logger.warn("Leave-impact notification failed for leave {}: {}", leave.getId(), ex.getMessage());
        }
    }
}