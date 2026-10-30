package com.mentis.hrms.pm.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.pm.dto.request.CreateApprovalRequest;
import com.mentis.hrms.pm.dto.response.ApprovalResponse;
import com.mentis.hrms.pm.dto.response.TimesheetResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.entity.Team;
import com.mentis.hrms.pm.entity.TeamMember;
import com.mentis.hrms.pm.entity.TimesheetWeek;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.TimesheetStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.pm.repository.TimesheetWeekRepository;
import com.mentis.hrms.pm.service.ApprovalService;
import com.mentis.hrms.pm.service.TimesheetService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.service.ProjectFinanceService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TimesheetServiceImpl implements TimesheetService {

    private final TaskTimeLogRepository timeLogRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TimesheetWeekRepository weekRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;
    private final ProjectFinanceService projectFinanceService;

    // ═══════════════════════════ READ ═══════════════════════════

    @Override
    @Transactional(readOnly = true)
    public TimesheetResponse forEmployee(Long employeePk, LocalDate weekStart) {
        return buildWeek(employeePk, weekStart, null);
    }

    /**
     * onlyProjectId == null : the person's whole week (self, team lead, HR/admin).
     * onlyProjectId != null : ONLY the hours logged on that project (project view for
     *                         owner / manager / lead). Hours on other projects stay private.
     */
    private TimesheetResponse buildWeek(Long employeePk, LocalDate weekStart, Long onlyProjectId) {
        Employee e = employeeRepository.findById(employeePk).orElse(null);
        if (e == null) return null;

        LocalDate weekEnd = weekStart.plusDays(6);
        List<TaskTimeLog> logs = timeLogRepository
                .findByEmployeeIdAndLogDateBetweenOrderByLogDateAsc(employeePk, weekStart, weekEnd);
        if (onlyProjectId != null) {
            logs = logs.stream()
                    .filter(l -> onlyProjectId.equals(
                            taskRepository.findById(l.getTaskId()).map(Task::getProjectId).orElse(null)))
                    .collect(Collectors.toList());
        }

        Map<LocalDate, List<TaskTimeLog>> byDay = new LinkedHashMap<>();
        for (LocalDate d = weekStart; !d.isAfter(weekEnd); d = d.plusDays(1)) {
            byDay.put(d, new ArrayList<>());
        }
        for (TaskTimeLog l : logs) {
            byDay.computeIfAbsent(l.getLogDate(), k -> new ArrayList<>()).add(l);
        }

        List<TimesheetResponse.Day> days = new ArrayList<>();
        int weekMinutes = 0;
        int weekBillable = 0;
        int weekOvertime = 0;
        for (Map.Entry<LocalDate, List<TaskTimeLog>> entry : byDay.entrySet()) {
            LocalDate date = entry.getKey();
            List<TaskTimeLog> entries = entry.getValue();
            int dayMinutes = entries.stream().mapToInt(TaskTimeLog::getMinutes).sum();
            // null / missing flag counts as billable (column default is 1)
            int dayBillable = entries.stream()
                    .filter(x -> !Boolean.FALSE.equals(x.getBillable()))
                    .mapToInt(TaskTimeLog::getMinutes).sum();
            int dayOvertime = entries.stream()
                    .filter(x -> Boolean.TRUE.equals(x.getOvertime()))
                    .mapToInt(TaskTimeLog::getMinutes).sum();
            weekMinutes += dayMinutes;
            weekBillable += dayBillable;
            weekOvertime += dayOvertime;

            List<TimesheetResponse.Entry> entryDtos = new ArrayList<>();
            for (TaskTimeLog l : entries) {
                Task t = taskRepository.findById(l.getTaskId()).orElse(null);
                if (t == null) continue;
                Project p = t.getProjectId() != null
                        ? projectRepository.findById(t.getProjectId()).orElse(null) : null;
                entryDtos.add(TimesheetResponse.Entry.builder()
                        .timeLogId(l.getId())
                        .taskId(t.getId()).taskTitle(t.getTitle())
                        .projectId(p != null ? p.getId() : null)
                        .projectName(p != null ? p.getName() : null)
                        .minutes(l.getMinutes())
                        .note(l.getNote())
                        .billable(l.getBillable())
                        .overtime(l.getOvertime())
                        .build());
            }
            days.add(TimesheetResponse.Day.builder()
                    .date(date)
                    .weekday(date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH))
                    .minutes(dayMinutes)
                    .hours(round1(dayMinutes / 60.0))
                    .billableMinutes(dayBillable)
                    .billableHours(round1(dayBillable / 60.0))
                    .overtimeMinutes(dayOvertime)
                    .overtimeHours(round1(dayOvertime / 60.0))
                    .entries(entryDtos)
                    .build());
        }

        TimesheetWeek wk = weekRepository.findByEmployeeIdAndWeekStart(employeePk, weekStart).orElse(null);
        TimesheetStatus st = wk == null || wk.getStatus() == null ? TimesheetStatus.DRAFT : wk.getStatus();

        return TimesheetResponse.builder()
                .employeePk(employeePk)
                .employeeId(e.getEmployeeId())
                .employeeName(safeName(e))
                .department(e.getDepartment())
                .weekStart(weekStart).weekEnd(weekEnd)
                .totalMinutes(weekMinutes)
                .totalHours(round1(weekMinutes / 60.0))
                .billableMinutes(weekBillable)
                .billableHours(round1(weekBillable / 60.0))
                .overtimeMinutes(weekOvertime)
                .overtimeHours(round1(weekOvertime / 60.0))
                .days(days)
                .status(st.name())
                // a project-scoped view is never editable
                .editable(onlyProjectId == null && (st == TimesheetStatus.DRAFT || st == TimesheetStatus.REJECTED))
                .statusNote(wk != null ? wk.getNote() : null)
                .submittedAt(wk != null ? wk.getSubmittedAt() : null)
                .decidedAt(wk != null ? wk.getDecidedAt() : null)
                .decidedByName(wk != null && wk.getDecidedBy() != null ? nameOf(wk.getDecidedBy()) : null)
                .approverId(wk != null ? wk.getApproverId() : null)
                .approverName(wk != null && wk.getApproverId() != null ? nameOf(wk.getApproverId()) : null)
                .approvalId(wk != null ? wk.getApprovalId() : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimesheetResponse> forTeam(Long teamId, LocalDate weekStart) {
        List<Long> pks = teamMemberRepository.findByTeamId(teamId).stream()
                .map(TeamMember::getEmployeeId).toList();
        return pks.stream().map(pk -> forEmployee(pk, weekStart))
                .filter(Objects::nonNull).collect(Collectors.toList());
    }
    @Override
    @Transactional(readOnly = true)
    public List<TimesheetResponse> forProject(Long projectId, LocalDate weekStart) {
        List<Long> pks = taskRepository.findByProjectId(projectId).stream()
                .flatMap(t -> taskAssignmentRepository.findByTaskId(t.getId()).stream())
                .map(TaskAssignment::getEmployeeId)
                .distinct().toList();
        return pks.stream().map(pk -> forEmployee(pk, weekStart))
                .filter(Objects::nonNull)
                .map(r -> onlyThisProject(r, projectId))   // STEP 6: privacy
                .filter(r -> r.getTotalMinutes() > 0)      // hide people with no hours on this project
                .collect(Collectors.toList());
    }

    /** STEP 6: a copy of one person's week that keeps ONLY the hours logged on this project. */
    /** STEP 6: a copy of one person's week that keeps ONLY the hours logged on this project. */
    private TimesheetResponse onlyThisProject(TimesheetResponse r, Long projectId) {
        List<TimesheetResponse.Day> days = new ArrayList<>();
        int total = 0;
        int totalBillable = 0;
        int totalOvertime = 0;
        for (TimesheetResponse.Day d : r.getDays()) {
            List<TimesheetResponse.Entry> kept = d.getEntries().stream()
                    .filter(en -> Objects.equals(en.getProjectId(), projectId))
                    .collect(Collectors.toList());
            int mins = kept.stream().mapToInt(TimesheetResponse.Entry::getMinutes).sum();
            int bill = kept.stream().filter(en -> !Boolean.FALSE.equals(en.getBillable()))
                    .mapToInt(TimesheetResponse.Entry::getMinutes).sum();
            int ot = kept.stream().filter(en -> Boolean.TRUE.equals(en.getOvertime()))
                    .mapToInt(TimesheetResponse.Entry::getMinutes).sum();
            total += mins;
            totalBillable += bill;
            totalOvertime += ot;
            days.add(TimesheetResponse.Day.builder()
                    .date(d.getDate()).weekday(d.getWeekday())
                    .minutes(mins).hours(round1(mins / 60.0))
                    .billableMinutes(bill).billableHours(round1(bill / 60.0))
                    .overtimeMinutes(ot).overtimeHours(round1(ot / 60.0))
                    .entries(kept).build());
        }
        return TimesheetResponse.builder()
                .employeePk(r.getEmployeePk()).employeeId(r.getEmployeeId())
                .employeeName(r.getEmployeeName()).department(r.getDepartment())
                .weekStart(r.getWeekStart()).weekEnd(r.getWeekEnd())
                .totalMinutes(total).totalHours(round1(total / 60.0))
                .billableMinutes(totalBillable).billableHours(round1(totalBillable / 60.0))
                .overtimeMinutes(totalOvertime).overtimeHours(round1(totalOvertime / 60.0))
                .days(days)
                .status(r.getStatus())
                .editable(false) // read-only in the project view; no notes or approver details
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(Long teamId, LocalDate weekStart) {
        List<TimesheetResponse> rows = forTeam(teamId, weekStart);
        StringBuilder sb = new StringBuilder();
        sb.append("Employee ID,Employee Name,Weekday,Date,Hours,Task,Project,Note,Billable,Overtime\n");
        for (TimesheetResponse r : rows) {
            for (TimesheetResponse.Day d : r.getDays()) {
                for (TimesheetResponse.Entry en : d.getEntries()) {
                    sb.append(csv(r.getEmployeeId())).append(',')
                            .append(csv(r.getEmployeeName())).append(',')
                            .append(csv(d.getWeekday())).append(',')
                            .append(d.getDate()).append(',')
                            .append(round1(en.getMinutes() / 60.0)).append(',')
                            .append(csv(en.getTaskTitle())).append(',')
                            .append(csv(en.getProjectName())).append(',')
                            .append(csv(en.getNote())).append(',')
                            .append(Boolean.FALSE.equals(en.getBillable()) ? "No" : "Yes").append(',')
                            .append(Boolean.TRUE.equals(en.getOvertime()) ? "Yes" : "No").append('\n');
                }
            }
        }
        return sb.toString();
    }

    // ═══════════════════════════ SUBMIT ═══════════════════════════

    @Override
    @Transactional
    public TimesheetResponse submit(Long userId, LocalDate weekStart) {
        LocalDate ws = weekStart.with(DayOfWeek.MONDAY);
        if (ws.isAfter(LocalDate.now()))
            throw new BusinessRuleException("You can't submit a week that hasn't started yet");

        TimesheetWeek w = weekRepository.findByEmployeeIdAndWeekStart(userId, ws)
                .orElseGet(() -> TimesheetWeek.builder()
                        .employeeId(userId).weekStart(ws).status(TimesheetStatus.DRAFT).build());

        if (w.getStatus() == TimesheetStatus.SUBMITTED)
            throw new BusinessRuleException("This week is already submitted and waiting for approval");
        if (w.getStatus() == TimesheetStatus.APPROVED)
            throw new BusinessRuleException("This week is already approved");
        if (w.getStatus() == TimesheetStatus.LOCKED)
            throw new BusinessRuleException("This week is locked and can't be submitted");

        List<TaskTimeLog> logs = timeLogRepository
                .findByEmployeeIdAndLogDateBetweenOrderByLogDateAsc(userId, ws, ws.plusDays(6));
        int total = logs.stream().mapToInt(TaskTimeLog::getMinutes).sum();
        if (total <= 0)
            throw new BusinessRuleException("There is no time logged in this week");

        Long approverPk = pickApprover(userId, logs);

        w.setStatus(TimesheetStatus.SUBMITTED);
        w.setTotalMinutes(total);
        w.setSubmittedAt(LocalDateTime.now());
        w.setApproverId(approverPk);
        w.setDecidedBy(null);
        w.setDecidedAt(null);
        w.setNote(null);
        w = weekRepository.save(w);

        CreateApprovalRequest req = new CreateApprovalRequest();
        req.setEntityType("TIMESHEET");
        req.setEntityId(w.getId());
        req.setApproverId(approverPk);
        req.setReason("Timesheet for the week of " + ws);
        req.setPriority("NORMAL");
        req.setPayload(buildPayload(ws, total, logs));
        ApprovalResponse ar = approvalService.create(req, userId);

        w.setApprovalId(ar.getId());
        weekRepository.save(w);

        return forEmployee(userId, ws);
    }

    // ═══════════════════════════ REOPEN (admin) ═══════════════════════════

    @Override
    @Transactional
    public TimesheetResponse reopen(Long employeePk, LocalDate weekStart, Long actorPk) {
        LocalDate ws = weekStart.with(DayOfWeek.MONDAY);
        TimesheetWeek w = weekRepository.findByEmployeeIdAndWeekStart(employeePk, ws)
                .orElseThrow(() -> new ResourceNotFoundException("No timesheet record for that week"));
        if (w.getStatus() == TimesheetStatus.SUBMITTED)
            throw new BusinessRuleException(
                    "This week is waiting for approval. Ask the approver to decide, or the employee to withdraw it.");
        if (w.getStatus() != TimesheetStatus.DRAFT) {
            w.setStatus(TimesheetStatus.DRAFT);
            w.setApprovalId(null);
            w.setApproverId(null);
            w.setSubmittedAt(null);
            w.setDecidedBy(actorPk);
            w.setDecidedAt(LocalDateTime.now());
            w.setNote("Reopened by " + nameOf(actorPk));
            projectFinanceService.recalculateForEmployeeWeek(employeePk, ws);
            weekRepository.save(w);
        }
        return forEmployee(employeePk, ws);
    }

    @Override
    @Transactional
    public TimesheetResponse lock(Long weekId, Long actorPk) {
        TimesheetWeek w = weekRepository.findById(weekId)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found: " + weekId));
        if (w.getStatus() == TimesheetStatus.LOCKED)
            throw new BusinessRuleException("This week is already locked");
        w.setStatus(TimesheetStatus.LOCKED);
        w.setDecidedBy(actorPk);
        w.setDecidedAt(LocalDateTime.now());
        weekRepository.save(w);
        return forEmployee(w.getEmployeeId(), w.getWeekStart());
    }

    @Override
    @Transactional
    public TimesheetResponse unlock(Long weekId, Long actorPk) {
        TimesheetWeek w = weekRepository.findById(weekId)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found: " + weekId));
        if (w.getStatus() != TimesheetStatus.LOCKED)
            throw new BusinessRuleException("This week is not locked");
        w.setStatus(TimesheetStatus.DRAFT);
        w.setDecidedBy(actorPk);
        w.setDecidedAt(LocalDateTime.now());
        w.setNote("Unlocked by " + nameOf(actorPk));
        weekRepository.save(w);
        return forEmployee(w.getEmployeeId(), w.getWeekStart());
    }

    // ═══════════════════════════ approver choice ═══════════════════════════

    /**
     * Order: lead(s) of the employee's own teams, then for each project they logged on:
     * its LEAD members, its team lead, its owner, its MANAGER members; then HR_MANAGER / SUPER_ADMIN.
     * Anyone on approved leave today is skipped. The employee can never approve their own week.
     */
    private Long pickApprover(Long employeePk, List<TaskTimeLog> logs) {
        LinkedHashSet<Long> cands = new LinkedHashSet<>();

        for (TeamMember tm : teamMemberRepository.findByEmployeeId(employeePk)) {
            teamRepository.findById(tm.getTeamId()).map(Team::getLeadEmployeeId).ifPresent(cands::add);
        }

        LinkedHashSet<Long> projectIds = new LinkedHashSet<>();
        for (TaskTimeLog l : logs) {
            taskRepository.findById(l.getTaskId()).map(Task::getProjectId).ifPresent(projectIds::add);
        }
        for (Long pid : projectIds) {
            Project p = projectRepository.findById(pid).orElse(null);
            if (p == null) continue;

            if (p.getTeamId() != null) {
                teamRepository.findById(p.getTeamId()).map(Team::getLeadEmployeeId).ifPresent(cands::add);
            }
            // STEP 6: explicit project LEADs run the day to day, so they come before owner / managers
            projectMemberRepository.findByProjectId(pid).stream()
                    .filter(m -> m.getRole() == ProjectRole.LEAD)
                    .forEach(m -> cands.add(m.getEmployeeId()));
            if (p.getOwnerId() != null) cands.add(p.getOwnerId());
            projectMemberRepository.findByProjectId(pid).stream()
                    .filter(m -> m.getRole() == ProjectRole.MANAGER)
                    .forEach(m -> cands.add(m.getEmployeeId()));
        }
        cands.remove(employeePk);

        for (Long c : cands) {
            if (!unavailable(c)) return c;
        }

        List<Long> admins = new ArrayList<>();
        for (Employee a : employeeRepository.findAll()) {
            UserRole r = a.getRole();
            if ((r == UserRole.HR_MANAGER || r == UserRole.SUPER_ADMIN) && !a.getId().equals(employeePk)) {
                admins.add(a.getId());
            }
        }
        for (Long a : admins) {
            if (!unavailable(a)) return a;
        }

        if (!cands.isEmpty()) return cands.iterator().next();
        if (!admins.isEmpty()) return admins.get(0);
        throw new BusinessRuleException(
                "No approver found. Ask an admin to set a team lead or project owner.");
    }

    /** true when the employee doesn't exist or is on APPROVED leave today. */
    private boolean unavailable(Long pk) {
        Employee e = employeeRepository.findById(pk).orElse(null);
        if (e == null) return true;
        LocalDate today = LocalDate.now();
        return !leaveRequestRepository
                .findApprovedLeavesForEmployeeInRange(e.getEmployeeId(), today, today).isEmpty();
    }

    // ═══════════════════════════ helpers ═══════════════════════════

    /** Snapshot shown to the approver in the inbox (they can't always open the timesheet). */
    private String buildPayload(LocalDate ws, int total, List<TaskTimeLog> logs) {
        Map<Long, Integer> byProject = new LinkedHashMap<>();
        Map<LocalDate, Integer> byDay = new TreeMap<>();
        for (TaskTimeLog l : logs) {
            Long pid = taskRepository.findById(l.getTaskId()).map(Task::getProjectId).orElse(-1L);
            byProject.merge(pid, l.getMinutes(), Integer::sum);
            byDay.merge(l.getLogDate(), l.getMinutes(), Integer::sum);
        }
        List<Map<String, Object>> proj = new ArrayList<>();
        byProject.forEach((pid, minutes) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("projectId", pid);
            x.put("projectName", pid < 0 ? "No project"
                    : projectRepository.findById(pid).map(Project::getName).orElse("Project #" + pid));
            x.put("minutes", minutes);
            proj.add(x);
        });
        List<Map<String, Object>> day = new ArrayList<>();
        byDay.forEach((d, minutes) -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("date", d.toString());
            x.put("minutes", minutes);
            day.add(x);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("weekStart", ws.toString());
        root.put("totalMinutes", total);
        root.put("byProject", proj);
        root.put("byDay", day);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.warn("timesheet payload failed: {}", ex.getMessage());
            return null;
        }
    }

    private String nameOf(Long pk) {
        if (pk == null) return null;
        return employeeRepository.findById(pk).map(this::safeName).orElse("Employee #" + pk);
    }

    private String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private double round1(double x) { return Math.round(x * 10.0) / 10.0; }

    private String safeName(Employee e) {
        String f = e.getFirstName() == null ? "" : e.getFirstName();
        String l = e.getLastName() == null ? "" : e.getLastName();
        String full = (f + " " + l).trim();
        return full.isEmpty() ? ("Employee #" + e.getId()) : full;
    }
}