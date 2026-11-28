package com.mentis.hrms.pm.service;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.Holiday;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.TeamMember;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.util.WorkCalendar;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.HolidayRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PM-041 — the bridge between HR (holidays / approved leave / attendance) and project planning.
 *
 * HR tables are the single source of truth: pm_holidays is NOT duplicated here, nothing is
 * copied into PM tables. This service only *reads* holidays and leave requests and turns them
 * into the numbers a project manager needs: who is really available, on which days, and how
 * many hours a person can actually contribute in a window.
 *
 * Used by: CalendarController, ProjectTimelineController (Gantt leave overlay),
 * WorkloadServiceImpl (already reads the same tables), and the PM Home "today" widget.
 */
@Service
@RequiredArgsConstructor
public class PmCalendarService {

    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final WorkCalendar workCalendar;

    @Value("${hrms.workday.hours:8}")
    private double hoursPerDay;

    /* ───────────────────────── holidays ───────────────────────── */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> holidays(LocalDate from, LocalDate to) {
        LocalDate a = from == null ? LocalDate.now().minusMonths(1) : from;
        LocalDate b = to == null ? LocalDate.now().plusMonths(6) : to;
        return holidayRepository.findByHolidayDateBetween(a, b).stream()
                .sorted(Comparator.comparing(Holiday::getHolidayDate))
                .map(h -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", h.getHolidayDate());
                    m.put("name", h.getHolidayName());
                    m.put("type", h.getHolidayType());
                    return m;
                }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDates(LocalDate from, LocalDate to) {
        return workCalendar.holidays(from, to);
    }

    /* ───────────────────────── one person ───────────────────────── */

    /**
     * Every day in the window on which the person cannot work, with the reason.
     * kind = HOLIDAY | WEEKLY_OFF | LEAVE
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> unavailableDays(Long employeePk, LocalDate from, LocalDate to) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (employeePk == null || from == null || to == null || to.isBefore(from)) return out;

        Set<LocalDate> hol = workCalendar.holidays(from, to);
        Set<LocalDate> leaveDays = new LinkedHashSet<>();
        String empCode = employeeCode(employeePk);
        if (empCode != null) {
            for (LeaveRequest l : leaveRequestRepository.findApprovedLeavesForEmployeeInRange(empCode, from, to)) {
                if (Boolean.TRUE.equals(l.getWithdrawn())) continue;
                for (LocalDate d = max(l.getStartDate(), from); !d.isAfter(min(l.getEndDate(), to)); d = d.plusDays(1)) {
                    leaveDays.add(d);
                }
            }
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            if (leaveDays.contains(d)) m.put("kind", "LEAVE");
            else if (hol.contains(d)) m.put("kind", "HOLIDAY");
            else if (workCalendar.isWeeklyOff(d)) m.put("kind", "WEEKLY_OFF");
            else continue;                      // a normal working day — not returned
            out.add(m);
        }
        return out;
    }

    /** hours the person can really contribute inside the window (working days − leave) × hoursPerDay */
    @Transactional(readOnly = true)
    public double capacityHours(Long employeePk, LocalDate from, LocalDate to) {
        if (employeePk == null || from == null || to == null || to.isBefore(from)) return 0d;
        Set<LocalDate> hol = workCalendar.holidays(from, to);
        int days = workCalendar.workingDays(from, to, hol);
        String empCode = employeeCode(employeePk);
        if (empCode != null) {
            for (LeaveRequest l : leaveRequestRepository.findApprovedLeavesForEmployeeInRange(empCode, from, to)) {
                if (Boolean.TRUE.equals(l.getWithdrawn())) continue;
                int off = workCalendar.workingDays(max(l.getStartDate(), from), min(l.getEndDate(), to), hol);
                days -= off;
            }
        }
        return Math.max(days, 0) * hoursPerDay;
    }

    /* ───────────────────────── a project ───────────────────────── */

    /**
     * Who is on this project, how many of the window's working days each person is actually
     * available, and why. The manager uses this before promising a date.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> projectAvailability(Long projectId, LocalDate from, LocalDate to) {
        LocalDate a = from == null ? LocalDate.now() : from;
        LocalDate b = to == null ? a.plusDays(30) : to;
        Set<Long> people = peopleOnProject(projectId);
        Set<LocalDate> hol = workCalendar.holidays(a, b);
        int workingDays = workCalendar.workingDays(a, b, hol);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long pk : people) {
            Employee e = employeeRepository.findById(pk).orElse(null);
            if (e == null) continue;
            int leaveDays = 0;
            if (e.getEmployeeId() != null) {
                for (LeaveRequest l : leaveRequestRepository
                        .findApprovedLeavesForEmployeeInRange(e.getEmployeeId(), a, b)) {
                    if (Boolean.TRUE.equals(l.getWithdrawn())) continue;
                    leaveDays += workCalendar.workingDays(max(l.getStartDate(), a), min(l.getEndDate(), b), hol);
                }
            }
            int available = Math.max(workingDays - leaveDays, 0);
            int pct = workingDays == 0 ? 0 : Math.round(available * 100f / workingDays);

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", pk);
            m.put("name", fullName(e));
            m.put("workingDays", workingDays);
            m.put("leaveDays", leaveDays);
            m.put("availableDays", available);
            m.put("availabilityPct", pct);
            m.put("capacityHours", available * hoursPerDay);
            rows.add(m);
        }
        rows.sort(Comparator.comparing(r -> (Integer) r.get("availabilityPct")));
        return rows;
    }

    /**
     * Pending (not yet approved) leave of the people on this project.
     * A pending request is not an absence yet, but a manager planning a deadline must see it —
     * this is the "please don't commit to that date" list.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingLeave(Long projectId, LocalDate from, LocalDate to) {
        LocalDate a = from == null ? LocalDate.now() : from;
        LocalDate b = to == null ? a.plusDays(90) : to;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long pk : peopleOnProject(projectId)) {
            Employee e = employeeRepository.findById(pk).orElse(null);
            if (e == null || e.getEmployeeId() == null) continue;
            for (LeaveRequest l : leaveRequestRepository.findByEmployeeIdAndStatus(e.getEmployeeId(), "PENDING")) {
                if (Boolean.TRUE.equals(l.getWithdrawn())) continue;
                if (l.getEndDate() == null || l.getStartDate() == null) continue;
                if (l.getEndDate().isBefore(a) || l.getStartDate().isAfter(b)) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("employeeId", pk);
                m.put("name", fullName(e));
                m.put("startDate", l.getStartDate());
                m.put("endDate", l.getEndDate());
                m.put("days", l.getTotalDays() != null ? l.getTotalDays()
                        : ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1);
                m.put("leaveType", l.getLeaveType());
                m.put("requestId", l.getId());
                rows.add(m);
            }
        }
        return rows;
    }

    /** "is today a working day" — used by the Home page widget */
    @Transactional(readOnly = true)
    public Map<String, Object> today() {
        LocalDate d = LocalDate.now();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", d);
        m.put("weeklyOff", workCalendar.isWeeklyOff(d));
        List<Holiday> hol = holidayRepository.findByHolidayDate(d);
        m.put("holiday", hol.isEmpty() ? null : hol.get(0).getHolidayName());
        m.put("holidayType", hol.isEmpty() ? null : hol.get(0).getHolidayType());
        m.put("workingDay", !workCalendar.isWeeklyOff(d) && hol.isEmpty());
        return m;
    }

    /* ───────────────────────── helpers ───────────────────────── */

    private Set<Long> peopleOnProject(Long projectId) {
        Set<Long> pks = new LinkedHashSet<>();
        projectRepository.findById(projectId).ifPresent((Project p) -> {
            if (p.getOwnerId() != null) pks.add(p.getOwnerId());
            if (p.getTeamId() != null) {
                for (TeamMember tm : teamMemberRepository.findByTeamId(p.getTeamId())) {
                    if (tm.getEmployeeId() != null) pks.add(tm.getEmployeeId());
                }
            }
        });
        projectMemberRepository.findByProjectId(projectId)
                .forEach(pm -> { if (pm.getEmployeeId() != null) pks.add(pm.getEmployeeId()); });
        return pks;
    }

    private String employeeCode(Long pk) {
        return employeeRepository.findById(pk).map(Employee::getEmployeeId).orElse(null);
    }

    private String fullName(Employee e) {
        String n = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                + (e.getLastName() == null ? "" : e.getLastName())).trim();
        return n.isEmpty() ? (e.getEmail() == null ? "Employee #" + e.getId() : e.getEmail()) : n;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isBefore(b) ? a : b;
    }

    private static LocalDate max(LocalDate a, LocalDate b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
