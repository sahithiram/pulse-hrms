package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.entity.RecurringTaskRule;
import com.mentis.hrms.pm.enums.TaskPriority;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.RecurringTaskRuleRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;   // CHANGED
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTaskService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final int MAX_CATCHUP = 7;          // create at most the 7 newest missed occurrences per run
    static final int MAX_STEPS = 1000;         // safety stop for the date loop
    static final Set<String> FREQ = Set.of("DAILY", "WEEKLY", "MONTHLY");

    public record RuleRequest(String title, String description, TaskPriority priority, Long assigneeId,
                              String frequency, Integer dayOfWeek, Integer dayOfMonth, Integer dueAfterDays,
                              LocalDate startDate, LocalDate endDate) {}

    public record RuleView(Long id, Long projectId, String title, String description, String priority,
                           Long assigneeId, String frequency, Integer dayOfWeek, Integer dayOfMonth,
                           int dueAfterDays, LocalDate startDate, LocalDate endDate,
                           LocalDate nextRunDate, boolean paused, boolean canManage) {}

    private final RecurringTaskRuleRepository ruleRepo;
    private final RecurringTaskExecutor executor;
    private final PmAccessService access;

    /* ───────── date math (static: easy to unit test) ───────── */

    public static LocalDate firstRun(String freq, Integer dow, Integer dom, LocalDate from) {
        switch (freq) {
            case "WEEKLY":
                return from.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dow)));
            case "MONTHLY": {
                YearMonth ym = YearMonth.from(from);
                LocalDate c = ym.atDay(Math.min(dom, ym.lengthOfMonth()));
                if (c.isBefore(from)) { ym = ym.plusMonths(1); c = ym.atDay(Math.min(dom, ym.lengthOfMonth())); }
                return c;
            }
            default:
                return from;
        }
    }

    public static LocalDate next(RecurringTaskRule r, LocalDate after) {
        switch (r.getFrequency()) {
            case "WEEKLY":  return after.plusWeeks(1);
            case "MONTHLY": {
                YearMonth ym = YearMonth.from(after).plusMonths(1);
                return ym.atDay(Math.min(r.getDayOfMonth(), ym.lengthOfMonth()));
            }
            default:        return after.plusDays(1);
        }
    }

    /** First occurrence strictly after 'today'. */                                   // CHANGED (new helper)
    static LocalDate advancePast(RecurringTaskRule r, LocalDate today) {
        LocalDate n = r.getNextRunDate();
        int steps = 0;
        while (!n.isAfter(today) && steps++ < MAX_STEPS) n = next(r, n);
        return n;
    }

    /* ───────── permissions ───────── */

    private void requireView(Long userId, Long projectId) { access.requireCanViewProject(userId, projectId); }

    private void requireManage(Long userId, Long projectId) {
        access.requireCanViewProject(userId, projectId);
        if (access.isProjectLocked(projectId))
            throw new BusinessRuleException("This project is completed or archived, so recurring tasks are locked.");
        if (!access.canAdministerProject(userId, projectId))
            throw new PmAccessDeniedException("Only a manager can change recurring tasks.");
    }

    private RecurringTaskRule rule(Long id) {
        return ruleRepo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Recurring rule not found: " + id));
    }

    /* ───────── validation ───────── */

    private void validate(RuleRequest q) {
        if (q.title() == null || q.title().trim().isEmpty() || q.title().trim().length() > 300)
            throw new BusinessRuleException("Title is required (max 300 characters).");
        if (q.frequency() == null || !FREQ.contains(q.frequency()))
            throw new BusinessRuleException("Frequency must be DAILY, WEEKLY or MONTHLY.");
        if (q.frequency().equals("WEEKLY") && (q.dayOfWeek() == null || q.dayOfWeek() < 1 || q.dayOfWeek() > 7))
            throw new BusinessRuleException("Pick a weekday for a weekly task.");
        if (q.frequency().equals("MONTHLY") && (q.dayOfMonth() == null || q.dayOfMonth() < 1 || q.dayOfMonth() > 28))
            throw new BusinessRuleException("Day of month must be between 1 and 28.");
        if (q.dueAfterDays() != null && (q.dueAfterDays() < 0 || q.dueAfterDays() > 365))
            throw new BusinessRuleException("Due-after days must be between 0 and 365.");
        if (q.startDate() == null) throw new BusinessRuleException("Start date is required.");
        if (q.endDate() != null && q.endDate().isBefore(q.startDate()))
            throw new BusinessRuleException("End date cannot be before the start date.");
    }

    private void apply(RecurringTaskRule r, RuleRequest q) {
        LocalDate today = LocalDate.now(ZONE);
        r.setTitle(q.title().trim());
        r.setDescription(q.description());
        r.setPriority(q.priority() == null ? TaskPriority.MEDIUM : q.priority());
        r.setAssigneeId(q.assigneeId());
        r.setFrequency(q.frequency());
        r.setDayOfWeek(q.frequency().equals("WEEKLY") ? q.dayOfWeek() : null);
        r.setDayOfMonth(q.frequency().equals("MONTHLY") ? q.dayOfMonth() : null);
        r.setDueAfterDays(q.dueAfterDays() == null ? 0 : q.dueAfterDays());
        r.setStartDate(q.startDate());
        r.setEndDate(q.endDate());
        LocalDate from = q.startDate().isBefore(today) ? today : q.startDate();   // never back-fill the past on save
        r.setNextRunDate(firstRun(r.getFrequency(), r.getDayOfWeek(), r.getDayOfMonth(), from));
    }

    private RuleView view(RecurringTaskRule r, boolean canManage) {
        return new RuleView(r.getId(), r.getProjectId(), r.getTitle(), r.getDescription(), r.getPriority().name(),
                r.getAssigneeId(), r.getFrequency(), r.getDayOfWeek(), r.getDayOfMonth(), r.getDueAfterDays(),
                r.getStartDate(), r.getEndDate(), r.getNextRunDate(), r.isPaused(), canManage);
    }

    /* ───────── CRUD ───────── */

    @Transactional(readOnly = true)
    public List<RuleView> list(Long projectId, Long userId) {
        requireView(userId, projectId);
        boolean m = !access.isProjectLocked(projectId) && access.canAdministerProject(userId, projectId);
        return ruleRepo.findByProjectIdOrderByIdDesc(projectId).stream().map(r -> view(r, m)).collect(Collectors.toList());
    }

    @Transactional
    public RuleView create(Long projectId, RuleRequest q, Long userId) {
        requireManage(userId, projectId);
        validate(q);
        RecurringTaskRule r = RecurringTaskRule.builder().projectId(projectId).createdBy(userId).build();
        apply(r, q);
        return view(ruleRepo.save(r), true);
    }

    @Transactional
    public RuleView update(Long id, RuleRequest q, Long userId) {
        RecurringTaskRule r = rule(id);
        requireManage(userId, r.getProjectId());
        validate(q);
        apply(r, q);
        return view(ruleRepo.save(r), true);
    }

    @Transactional
    public void delete(Long id, Long userId) {
        RecurringTaskRule r = rule(id);
        requireManage(userId, r.getProjectId());
        ruleRepo.delete(r);                       // tasks already created stay
    }

    @Transactional
    public RuleView setPaused(Long id, boolean paused, Long userId) {
        RecurringTaskRule r = rule(id);
        requireManage(userId, r.getProjectId());
        if (!paused) {                            // resume: skip anything missed while paused
            LocalDate today = LocalDate.now(ZONE);
            if (r.getNextRunDate().isBefore(today))
                r.setNextRunDate(firstRun(r.getFrequency(), r.getDayOfWeek(), r.getDayOfMonth(), today));
            // CHANGED: a rule whose end date has passed cannot be resumed
            if (r.getEndDate() != null && r.getNextRunDate().isAfter(r.getEndDate()))
                throw new BusinessRuleException("This recurring task has ended. Edit its end date to run it again.");
        }
        r.setPaused(paused);
        return view(ruleRepo.save(r), true);
    }

    /* ───────── run ───────── */

    /** Scheduler entry: every due rule in every project. */
    public int runAll() {
        return runRules(ruleRepo.findByPausedFalseAndNextRunDateLessThanEqual(LocalDate.now(ZONE)));
    }

    /** Manual run for one project (manager). */
    public int runProject(Long projectId, Long userId) {
        requireManage(userId, projectId);
        return runRules(ruleRepo.findByProjectIdAndPausedFalseAndNextRunDateLessThanEqual(projectId, LocalDate.now(ZONE)));
    }

    int runRules(List<RecurringTaskRule> rules) {
        LocalDate today = LocalDate.now(ZONE);
        int created = 0;
        for (RecurringTaskRule r : rules) {
            try {
                if (access.isProjectLocked(r.getProjectId())) {
                    // CHANGED: skip creation, but move past today so reopening the project does not cause a burst
                    r.setNextRunDate(advancePast(r, today));
                    ruleRepo.save(r);
                    log.info("Recurring rule {} skipped: project {} is locked", r.getId(), r.getProjectId());
                    continue;
                }
                List<LocalDate> due = new ArrayList<>();
                int steps = 0;
                while (!r.getNextRunDate().isAfter(today) && steps++ < MAX_STEPS
                        && (r.getEndDate() == null || !r.getNextRunDate().isAfter(r.getEndDate()))) {
                    due.add(r.getNextRunDate());
                    r.setNextRunDate(next(r, r.getNextRunDate()));
                }
                int from = Math.max(0, due.size() - MAX_CATCHUP);          // keep the newest ones, drop old misses
                int made = 0, failed = 0;
                LocalDate firstFailed = null;                               // CHANGED
                for (LocalDate d : due.subList(from, due.size())) {
                    try {
                        if (executor.create(r, d) != null) { made++; created++; }
                    } catch (DataIntegrityViolationException dup) {         // CHANGED: another server made it first
                        log.info("Recurring rule {} occurrence {} already exists", r.getId(), d);
                    } catch (Exception ex) {
                        failed++;
                        if (firstFailed == null) firstFailed = d;
                        log.warn("Recurring rule {} occurrence {} failed: {}", r.getId(), d, ex.getMessage());
                    }
                }
                if (firstFailed != null) {
                    r.setNextRunDate(firstFailed);          // CHANGED: retry on the next run; the unique guard makes it safe
                } else if (r.getEndDate() != null && r.getNextRunDate().isAfter(r.getEndDate())) {
                    r.setPaused(true);                      // finished
                }
                ruleRepo.save(r);
                log.info("Recurring rule {} ({}): due={}, created={}, failed={}", r.getId(), r.getTitle(), due.size(), made, failed);
            } catch (Exception ex) {
                log.warn("Recurring rule {} failed: {}", r.getId(), ex.getMessage());
            }
        }
        return created;
    }
}