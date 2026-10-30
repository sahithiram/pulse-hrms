package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.LogTimeRequest;
import com.mentis.hrms.pm.dto.request.UpdateTimeLogRequest;
import com.mentis.hrms.pm.dto.response.TimeLogResponse;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.enums.TimesheetStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import com.mentis.hrms.pm.repository.TimesheetWeekRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskTimeLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskTimeLogServiceImpl implements TaskTimeLogService {

    private static final int MAX_MINUTES_PER_ENTRY = 960;   // 16 h
    private static final int MAX_MINUTES_PER_DAY = 1440;    // 24 h
    private static final int STANDARD_DAY_MINUTES = 480;    // 8 h
    private static final int STANDARD_WEEK_MINUTES = 2400;  // 40 h

    private final TaskTimeLogRepository repo;
    private final TaskRepository taskRepository;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;
    private final PmAccessService access;
    private final TimesheetWeekRepository weekRepository;

    // ───────────────────────── log ─────────────────────────

    @Override
    @Transactional
    public TimeLogResponse log(Long taskId, LogTimeRequest req, Long userId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        assertWeekEditable(userId, req.getLogDate());
        if (req.getLogDate().isAfter(LocalDate.now()))
            throw new BusinessRuleException("You cannot log time in the future");
        if (req.getMinutes() <= 0 || req.getMinutes() > MAX_MINUTES_PER_ENTRY)
            throw new BusinessRuleException("Minutes must be between 1 and " + MAX_MINUTES_PER_ENTRY);
        long alreadyThatDay = repo.sumMinutesByEmployeeAndRange(userId, req.getLogDate(), req.getLogDate());
        if (alreadyThatDay + req.getMinutes() > MAX_MINUTES_PER_DAY)
            throw new BusinessRuleException("More than 24h logged on that day");

        TaskTimeLog saved = repo.save(TaskTimeLog.builder()
                .taskId(taskId)
                .employeeId(userId)
                .logDate(req.getLogDate())
                .minutes(req.getMinutes())
                .note(req.getNote())
                .billable(req.getBillable() == null || req.getBillable())
                .overtime(false)               // set by recomputeOvertime below
                .build());

        recomputeOvertime(userId, req.getLogDate());
        updateTaskTotals(task);

        publisher.publish("POST", "/api/pm/tasks/" + taskId + "/time", String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimeLogResponse> list(Long taskId) {
        return repo.findByTaskIdOrderByLogDateDesc(taskId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ───────────────────────── update (NEW) ─────────────────────────

    /**
     * Changes billable / minutes / note of one entry. Only the fields that are sent are touched.
     * Same rules as log(): owner or admin, week must be editable, 1..960 min, max 24h per day.
     */
    @Override
    @Transactional
    public TimeLogResponse update(Long id, UpdateTimeLogRequest req, Long userId) {
        TaskTimeLog row = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Time log not found: " + id));

        if (!row.getEmployeeId().equals(userId) && !access.isAdmin(userId))
            throw new PmAccessDeniedException("You can only edit your own time logs.");

        assertWeekEditable(row.getEmployeeId(), row.getLogDate());

        boolean changed = false;
        boolean minutesChanged = false;

        if (req.getMinutes() != null) {
            int m = req.getMinutes();
            if (m <= 0 || m > MAX_MINUTES_PER_ENTRY)
                throw new BusinessRuleException("Minutes must be between 1 and " + MAX_MINUTES_PER_ENTRY);
            if (m != row.getMinutes()) {
                long others = repo.sumMinutesByEmployeeAndRange(row.getEmployeeId(), row.getLogDate(), row.getLogDate())
                        - row.getMinutes();
                if (others + m > MAX_MINUTES_PER_DAY)
                    throw new BusinessRuleException("More than 24h logged on that day");
                row.setMinutes(m);
                minutesChanged = true;
                changed = true;
            }
        }

        if (req.getBillable() != null && !req.getBillable().equals(row.getBillable())) {
            row.setBillable(req.getBillable());
            changed = true;
        }

        if (req.getNote() != null) {
            String n = req.getNote().trim();
            if (n.isEmpty()) n = null;
            if (!Objects.equals(n, row.getNote())) {
                row.setNote(n);
                changed = true;
            }
        }

        if (!changed) return toResponse(row);

        repo.save(row);

        if (minutesChanged) {
            // minutes affect the day / week totals, so every flag of that week may move
            recomputeOvertime(row.getEmployeeId(), row.getLogDate());
            taskRepository.findById(row.getTaskId()).ifPresent(this::updateTaskTotals);
        }

        publisher.publish("PUT", "/api/pm/tasks/time/" + id, String.valueOf(userId));
        return toResponse(row);
    }

    // ───────────────────────── delete ─────────────────────────

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        TaskTimeLog row = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Time log not found: " + id));

        if (!row.getEmployeeId().equals(userId) && !access.isAdmin(userId))
            throw new PmAccessDeniedException("You can only delete your own time logs.");

        assertWeekEditable(row.getEmployeeId(), row.getLogDate());
        Long taskId = row.getTaskId();
        Long employeeId = row.getEmployeeId();
        LocalDate date = row.getLogDate();

        repo.delete(row);
        recomputeOvertime(employeeId, date);   // later entries may no longer be overtime
        taskRepository.findById(taskId).ifPresent(this::updateTaskTotals);
        publisher.publish("DELETE", "/api/pm/tasks/time/" + id, String.valueOf(userId));
    }

    // ───────────────────────── helpers ─────────────────────────

    /** A SUBMITTED, APPROVED or LOCKED week can't get new, changed or deleted time logs. */
    private void assertWeekEditable(Long employeePk, LocalDate date) {
        LocalDate ws = date.with(DayOfWeek.MONDAY);
        weekRepository.findByEmployeeIdAndWeekStart(employeePk, ws).ifPresent(w -> {
            if (w.getStatus() == TimesheetStatus.SUBMITTED || w.getStatus() == TimesheetStatus.APPROVED
                    || w.getStatus() == TimesheetStatus.LOCKED)
                throw new BusinessRuleException("That week is " + w.getStatus()
                        + " and can't be edited. Withdraw it first, or ask an admin to reopen it.");
        });
    }

    /**
     * Re-flags every entry of the employee's ISO week (Mon..Sun), oldest first.
     * An entry is overtime when, counting it, that day is over 8h OR the week is over 40h.
     * Whole-entry flag (same behaviour as before). Only rows whose flag changes are written.
     * Safe to call because the callers already checked that the week is editable.
     */
    private void recomputeOvertime(Long employeePk, LocalDate anyDateInWeek) {
        LocalDate ws = anyDateInWeek.with(DayOfWeek.MONDAY);
        List<TaskTimeLog> logs = new ArrayList<>(
                repo.findByEmployeeIdAndLogDateBetweenOrderByLogDateAsc(employeePk, ws, ws.plusDays(6)));

        logs.sort((a, b) -> {
            int c = a.getLogDate().compareTo(b.getLogDate());
            if (c != 0) return c;
            if (a.getCreatedAt() != null && b.getCreatedAt() != null) {
                c = a.getCreatedAt().compareTo(b.getCreatedAt());
                if (c != 0) return c;
            }
            return Long.compare(a.getId() == null ? 0L : a.getId(), b.getId() == null ? 0L : b.getId());
        });

        List<TaskTimeLog> dirty = new ArrayList<>();
        LocalDate currentDay = null;
        int dayTotal = 0;
        int weekTotal = 0;

        for (TaskTimeLog l : logs) {
            if (!l.getLogDate().equals(currentDay)) {
                currentDay = l.getLogDate();
                dayTotal = 0;
            }
            dayTotal += l.getMinutes();
            weekTotal += l.getMinutes();

            boolean ot = dayTotal > STANDARD_DAY_MINUTES || weekTotal > STANDARD_WEEK_MINUTES;
            if (!Boolean.valueOf(ot).equals(l.getOvertime())) {
                l.setOvertime(ot);
                dirty.add(l);
            }
        }
        if (!dirty.isEmpty()) repo.saveAll(dirty);
    }

    private void updateTaskTotals(Task task) {
        long totalMin = repo.sumMinutes(task.getId());
        task.setLoggedMinutes((int) totalMin);
        task.setLoggedHours((int) Math.round(totalMin / 60.0));
        taskRepository.save(task);
    }

    private TimeLogResponse toResponse(TaskTimeLog l) {
        String name = employeeRepository.findById(l.getEmployeeId())
                .map(e -> {
                    String first = e.getFirstName() == null ? "" : e.getFirstName();
                    String last = e.getLastName() == null ? "" : e.getLastName();
                    String full = (first + " " + last).trim();
                    return full.isEmpty() ? ("Employee #" + l.getEmployeeId()) : full;
                })
                .orElse("Employee #" + l.getEmployeeId());

        return TimeLogResponse.builder()
                .id(l.getId()).taskId(l.getTaskId())
                .employeeId(l.getEmployeeId()).employeeName(name)
                .logDate(l.getLogDate()).minutes(l.getMinutes())
                .note(l.getNote()).createdAt(l.getCreatedAt())
                .billable(l.getBillable()).overtime(l.getOvertime())
                .build();
    }
}