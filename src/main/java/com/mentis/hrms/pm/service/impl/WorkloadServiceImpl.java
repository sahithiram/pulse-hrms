package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.Holiday;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.pm.dto.response.WorkloadResponse;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.Team;
import com.mentis.hrms.pm.entity.TeamMember;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.WorkloadService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.HolidayRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkloadServiceImpl implements WorkloadService {

    private static final double HOURS_PER_DAY = 8.0;

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskTimeLogRepository taskTimeLogRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamRepository teamRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;

    @Override
    @Transactional(readOnly = true)
    public WorkloadResponse forEmployee(Long employeePk, LocalDate from, LocalDate to) {
        Employee e = employeeRepository.findById(employeePk).orElse(null);
        if (e == null) return null;
        return compute(e, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkloadResponse> forTeam(Long teamId, LocalDate from, LocalDate to) {
        List<Long> pks = teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMember::getEmployeeId).toList();
        return forEmployees(pks, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkloadResponse> forProject(Long projectId, LocalDate from, LocalDate to) {
        List<Long> pks = taskRepository.findByProjectId(projectId).stream()
                .flatMap(t -> taskAssignmentRepository.findByTaskId(t.getId()).stream())
                .map(TaskAssignment::getEmployeeId)
                .distinct()
                .toList();
        return forEmployees(pks, from, to);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkloadResponse> forEmployees(List<Long> pks, LocalDate from, LocalDate to) {
        if (pks == null || pks.isEmpty()) return List.of();
        List<Employee> emps = employeeRepository.findAllById(pks);
        List<WorkloadResponse> out = new ArrayList<>();
        for (Employee e : emps) {
            WorkloadResponse r = compute(e, from, to);
            if (r != null) out.add(r);
        }
        out.sort(Comparator.comparingInt(WorkloadResponse::getUtilizationPct).reversed());
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public String capacityWarning(Long employeePk, LocalDate from, LocalDate to) {
        WorkloadResponse r = forEmployee(employeePk, from, to);
        if (r == null) return null;
        if (r.getUtilizationPct() > 100)
            return r.getEmployeeName() + " is at " + r.getUtilizationPct()
                    + "% capacity (" + (int) r.getAllocatedHours() + "h allocated / "
                    + (int) r.getCapacityHours() + "h available)";
        if (r.getLeaveDays() > 0)
            return r.getEmployeeName() + " has " + r.getLeaveDays()
                    + " day(s) of approved leave in this window — reduced capacity";
        return null;
    }

    // ── core ──

    private WorkloadResponse compute(Employee e, LocalDate from, LocalDate to) {
        if (from == null) from = LocalDate.now().with(DayOfWeek.MONDAY);
        if (to == null)   to   = from.plusDays(6);

        // Holidays in range
        Set<LocalDate> holidays = holidayRepository.findByHolidayDateBetween(from, to).stream()
                .map(Holiday::getHolidayDate).collect(Collectors.toSet());

        // Working days
        int workingDays = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) workingDays++;
        }

        // Approved leaves
        List<LeaveRequest> leaves = leaveRequestRepository
                .findApprovedLeavesForEmployeeInRange(e.getEmployeeId(), from, to);

        double leaveDays = 0.0;
        List<WorkloadResponse.LeaveBreakdown> leaveDtos = new ArrayList<>();
        for (LeaveRequest lr : leaves) {
            LocalDate s = lr.getStartDate().isBefore(from) ? from : lr.getStartDate();
            LocalDate t = lr.getEndDate().isAfter(to) ? to : lr.getEndDate();
            double days = 0.0;
            if ("half_day".equalsIgnoreCase(lr.getLeaveDuration())) {
                days = 0.5;
            } else {
                for (LocalDate d = s; !d.isAfter(t); d = d.plusDays(1)) {
                    if (d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d)) days++;
                }
            }
            leaveDays += days;
            leaveDtos.add(WorkloadResponse.LeaveBreakdown.builder()
                    .leaveId(lr.getId())
                    .leaveType(lr.getLeaveType())
                    .startDate(lr.getStartDate())
                    .endDate(lr.getEndDate())
                    .days(days)
                    .build());
        }

        double capacity = (workingDays * HOURS_PER_DAY - (leaveDays * HOURS_PER_DAY))
                * allocationFactor(e.getId());
        double leaveHours = leaveDays * HOURS_PER_DAY;
        double holidayHours = holidays.size() * HOURS_PER_DAY;

        // Open tasks in window
        Set<Long> taskIds = new LinkedHashSet<>();
        for (TaskAssignment a : taskAssignmentRepository.findByEmployeeId(e.getId())) {
            Task t = taskRepository.findById(a.getTaskId()).orElse(null);
            if (t == null || t.getStatus() == TaskStatus.DONE) continue;
            LocalDate anchor = t.getStartDate() != null ? t.getStartDate()
                    : (t.getDueDate() != null ? t.getDueDate() : null);
            if (anchor == null || !anchor.isAfter(to)) taskIds.add(t.getId());
        }

        double allocated = 0;
        List<WorkloadResponse.TaskBreakdown> taskDtos = new ArrayList<>();
        for (Long tid : taskIds) {
            Task t = taskRepository.findById(tid).orElse(null);
            if (t == null) continue;
            double h = t.getEstimateHours() != null
                    ? t.getEstimateHours()
                    : (t.getStoryPoints() != null ? t.getStoryPoints() * 4.0 : 0.0);
            allocated += h;
            taskDtos.add(WorkloadResponse.TaskBreakdown.builder()
                    .taskId(t.getId()).title(t.getTitle())
                    .status(t.getStatus() != null ? t.getStatus().name() : "TODO")
                    .priority(t.getPriority() != null ? t.getPriority().name() : "MEDIUM")
                    .estimateHours(t.getEstimateHours())
                    .startDate(t.getStartDate()).dueDate(t.getDueDate())
                    .build());
        }

        long minutes = taskTimeLogRepository.sumMinutesByEmployeeAndRange(e.getId(), from, to);
        double logged = minutes / 60.0;

        int util = capacity <= 0 ? 0 : (int) Math.round(allocated * 100.0 / capacity);
        String status = util > 100 ? "OVER" : util > 85 ? "WARN"
                : (util < 40 && capacity > 0) ? "UNDER" : "OK";

        Long teamId = null; String teamName = null;
        List<TeamMember> memberships = teamMemberRepository.findByEmployeeId(e.getId());
        if (!memberships.isEmpty()) {
            teamId = memberships.get(0).getTeamId();
            teamName = teamRepository.findById(teamId).map(Team::getName).orElse(null);
        }

        return WorkloadResponse.builder()
                .employeePk(e.getId())
                .employeeId(e.getEmployeeId())
                .employeeName(safeName(e))
                .department(e.getDepartment())
                .teamId(teamId).teamName(teamName)
                .from(from).to(to)
                .capacityHours(round1(capacity))
                .leaveHours(round1(leaveHours))
                .holidayHours(round1(holidayHours))
                .allocatedHours(round1(allocated))
                .loggedHours(round1(logged))
                .workingDays(workingDays)
                .leaveDays((int) Math.round(leaveDays))
                .utilizationPct(util)
                .status(status)
                .leaves(leaveDtos).tasks(taskDtos)
                .build();
    }
    /** Share (0..1) of the person's time available to teams, from team-member allocation %. 1.0 if unset. */
    private double allocationFactor(Long employeePk) {
        List<TeamMember> ms = teamMemberRepository.findByEmployeeId(employeePk);
        if (ms.isEmpty()) return 1.0;
        int total = 0;
        for (TeamMember m : ms) total += (m.getAllocationPct() == null ? 100 : m.getAllocationPct());
        return Math.min(1.0, Math.max(0.0, total / 100.0));
    }
    private double round1(double x) { return Math.round(x * 10.0) / 10.0; }
    private String safeName(Employee e) {
        String f = e.getFirstName() == null ? "" : e.getFirstName();
        String l = e.getLastName() == null ? "" : e.getLastName();
        String full = (f + " " + l).trim();
        return full.isEmpty() ? ("Employee #" + e.getId()) : full;
    }
}