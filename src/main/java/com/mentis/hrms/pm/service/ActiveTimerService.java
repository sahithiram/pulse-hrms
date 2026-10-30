package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.LogTimeRequest;
import com.mentis.hrms.pm.entity.ActiveTimer;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActiveTimerService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final long MAX_MINUTES = 960; // 16 hours = per-entry limit

    public record TimerView(boolean running, Long taskId, String taskTitle, Long projectId, String projectName,
                            String note, boolean billable, long startedAtMs, long serverNowMs) {}
    public record StopResult(boolean needsConfirm, long ranMinutes, long loggedMinutes, int entries, boolean capped) {}
    record Seg(LocalDate date, long minutes) {}

    private final ActiveTimerRepository timerRepo;
    private final TaskRepository taskRepo;
    private final ProjectRepository projectRepo;
    private final TaskTimeLogService timeLogService;
    private final PmAccessService access;

    private static long ms(LocalDateTime t) { return t.atZone(ZONE).toInstant().toEpochMilli(); }

    /* ───────── read ───────── */

    @Transactional(readOnly = true)
    public TimerView current(Long userId) {
        long now = System.currentTimeMillis();
        ActiveTimer t = timerRepo.findByEmployeeId(userId).orElse(null);
        if (t == null) return new TimerView(false, null, null, null, null, null, false, 0, now);
        Task task = taskRepo.findById(t.getTaskId()).orElse(null);
        String project = task == null ? null
                : projectRepo.findById(task.getProjectId()).map(Project::getName).orElse(null);
        return new TimerView(true, t.getTaskId(), task == null ? "Task #" + t.getTaskId() : task.getTitle(),
                task == null ? null : task.getProjectId(), project, t.getNote(), t.isBillable(),
                ms(t.getStartedAt()), now);
    }

    /* ───────── start ───────── */

    @Transactional
    public TimerView start(Long taskId, String note, Boolean billable, Long userId) {
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        access.requireCanViewProject(userId, task.getProjectId());
        if (access.isProjectLocked(task.getProjectId()))
            throw new BusinessRuleException("This project is completed or archived, so time cannot be logged.");
        if (!access.canEditTaskAccess(userId, taskId))
            throw new PmAccessDeniedException("You can't log time on this task.");

        var running = timerRepo.findByEmployeeId(userId);
        if (running.isPresent()) {
            String title = taskRepo.findById(running.get().getTaskId()).map(Task::getTitle).orElse("another task");
            throw new BusinessRuleException("A timer is already running on \"" + title + "\". Stop it first.");
        }
        String n = note == null ? null : note.trim();
        if (n != null && n.length() > 500) n = n.substring(0, 500);
        try {
            timerRepo.saveAndFlush(ActiveTimer.builder().employeeId(userId).taskId(taskId)
                    .startedAt(LocalDateTime.now(ZONE)).note(n == null || n.isEmpty() ? null : n)
                    .billable(billable == null || billable).build());
        } catch (DataIntegrityViolationException ex) { // double tap / two devices at once
            throw new BusinessRuleException("A timer is already running. Stop it first.");
        }
        return current(userId);
    }

    /* ───────── stop ───────── */

    @Transactional
    public StopResult stop(boolean confirm, Long userId) {
        ActiveTimer t = timerRepo.findByEmployeeId(userId)
                .orElseThrow(() -> new BusinessRuleException("No timer is running."));
        Task task = taskRepo.findById(t.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "The task of this timer no longer exists. Discard the timer."));

        LocalDateTime start = t.getStartedAt(), end = LocalDateTime.now(ZONE);
        long seconds = Math.max(0, Duration.between(start, end).getSeconds());
        long ran = Math.max(1, (seconds + 59) / 60); // round up, minimum 1 minute

        boolean capped = ran > MAX_MINUTES;
        if (capped && !confirm) return new StopResult(true, ran, 0, 0, false);
        if (capped) end = start.plusMinutes(MAX_MINUTES);

        List<Seg> segs = split(start, end);
        long total = 0;
        for (Seg s : segs) {
            // log() joins this transaction: if any day fails (locked week, 24h/day), everything rolls back
            // and the timer row is kept.
            LogTimeRequest req = LogTimeRequest.builder()
                    .logDate(s.date())
                    .minutes((int) s.minutes())
                    .note(t.getNote())
                    .billable(t.isBillable())
                    .build();
            timeLogService.log(t.getTaskId(), req, userId);
            total += s.minutes();
        }
        timerRepo.delete(t);
        log.info("Timer stopped: employee={}, task={}, ran={}min, logged={}min in {} entr(ies)",
                userId, task.getId(), ran, total, segs.size());
        return new StopResult(false, ran, total, segs.size(), capped);
    }

    /** Splits [start, end) at every midnight; each piece is rounded up to whole minutes (minimum 1). */
    static List<Seg> split(LocalDateTime start, LocalDateTime end) {
        List<Seg> out = new ArrayList<>();
        LocalDateTime cur = start;
        while (cur.toLocalDate().isBefore(end.toLocalDate())) {
            LocalDateTime midnight = cur.toLocalDate().plusDays(1).atStartOfDay();
            add(out, cur.toLocalDate(), Duration.between(cur, midnight).getSeconds());
            cur = midnight;
        }
        add(out, cur.toLocalDate(), Duration.between(cur, end).getSeconds());
        if (out.isEmpty()) out.add(new Seg(start.toLocalDate(), 1));
        return out;
    }

    private static void add(List<Seg> out, LocalDate d, long seconds) {
        if (seconds <= 0) return;
        out.add(new Seg(d, Math.max(1, (seconds + 59) / 60)));
    }

    /* ───────── discard ───────── */

    @Transactional
    public void discard(Long userId) {
        timerRepo.findByEmployeeId(userId).ifPresent(timerRepo::delete);
    }
}