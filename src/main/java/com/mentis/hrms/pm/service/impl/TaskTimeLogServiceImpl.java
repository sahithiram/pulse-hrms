package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.LogTimeRequest;
import com.mentis.hrms.pm.dto.response.TimeLogResponse;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskTimeLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import com.mentis.hrms.pm.enums.TimesheetStatus;
import com.mentis.hrms.pm.repository.TimesheetWeekRepository;
import java.time.DayOfWeek;
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskTimeLogServiceImpl implements TaskTimeLogService {

    private static final int MAX_MINUTES_PER_ENTRY = 960;   // 16 h
    private static final int MAX_MINUTES_PER_DAY   = 1440;  // 24 h

    private final TaskTimeLogRepository repo;
    private final TaskRepository taskRepository;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;
    private final PmAccessService access;
    private final TimesheetWeekRepository weekRepository;

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
                .build());

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

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        TaskTimeLog row = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Time log not found: " + id));

        if (!row.getEmployeeId().equals(userId) && !access.isAdmin(userId))
            throw new PmAccessDeniedException("You can only delete your own time logs.");

        assertWeekEditable(row.getEmployeeId(), row.getLogDate());
        Long taskId = row.getTaskId();
        repo.delete(row);
        taskRepository.findById(taskId).ifPresent(this::updateTaskTotals);
        publisher.publish("DELETE", "/api/pm/tasks/time/" + id, String.valueOf(userId));
    }    /** A SUBMITTED or APPROVED week is locked: no new, changed or deleted time logs. */
    private void assertWeekEditable(Long employeePk, LocalDate date) {
        LocalDate ws = date.with(DayOfWeek.MONDAY);
        weekRepository.findByEmployeeIdAndWeekStart(employeePk, ws).ifPresent(w -> {
            if (w.getStatus() == TimesheetStatus.SUBMITTED || w.getStatus() == TimesheetStatus.APPROVED)
                throw new BusinessRuleException("That week is " + w.getStatus()
                        + " and can't be edited. Withdraw it first, or ask an admin to reopen it.");
        });
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
                .build();
    }
}