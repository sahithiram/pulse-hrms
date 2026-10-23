package com.mentis.hrms.pm.service;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.response.ProjectFinanceResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.Issue;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.entity.Risk;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.enums.HealthMode;
import com.mentis.hrms.pm.enums.ProjectHealth;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.enums.TimesheetStatus;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.IssueRepository;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.RiskRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import com.mentis.hrms.pm.repository.TimesheetWeekRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Baseline variance, actual cost (approved timesheets × hourly rate) and automatic project health.
 * Never calls PmAccessService: it is also used by schedulers that have no logged-in user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectFinanceService {

    // ───────────────── thresholds: tune the rules here ─────────────────
    private static final double SLIP_AT_RISK = 15.0;            // % points behind schedule
    private static final double SLIP_DELAYED = 30.0;
    private static final double BURN_WARN = 0.85;               // 85% of the budget used …
    private static final int    BURN_WARN_MAX_PROGRESS = 75;    // … while less than 75% done
    private static final double BURN_OVER = 1.0;                // over budget
    private static final double OVERDUE_RATIO_WARN = 0.25;      // > 25% of open tasks overdue
    private static final int    BLOCKED_WARN = 3;
    private static final int    CRITICAL_ISSUES_WARN = 1;
    private static final int    CRITICAL_ISSUES_CRITICAL = 3;
    private static final int    HIGH_RISKS_WARN = 2;
    /** true: only APPROVED timesheet weeks count as cost. false: every logged hour counts. */
    private static final boolean COUNT_ONLY_APPROVED = true;

    private static final Set<ProjectStatus> TRACKED =
            EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.AT_RISK, ProjectStatus.DELAYED);

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final IssueRepository issueRepository;
    private final RiskRepository riskRepository;
    private final TaskTimeLogRepository timeLogRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TimesheetWeekRepository weekRepository;
    private final EmployeeRepository employeeRepository;
    private final ActivityLogRepository activityLogRepository;
    private final NotificationEmitter notificationEmitter;

    // ═════════════════════════ results ═════════════════════════

    private record CostResult(BigDecimal approved, BigDecimal pending,
                              List<ProjectFinanceResponse.CostLine> lines, List<String> warnings) {
        BigDecimal actual() { return COUNT_ONLY_APPROVED ? approved : approved.add(pending); }
    }

    private record HealthResult(ProjectHealth level, List<String> reasons, double elapsedPct,
                                double slipPct, double burnPct, int progress,
                                long open, long overdue, long blocked,
                                long criticalIssues, long highRisks) { }

    // ═════════════════════════ read: the panel ═════════════════════════

    @Transactional(readOnly = true)
    public ProjectFinanceResponse finance(Long projectId) {
        Project p = find(projectId);
        CostResult c = computeCost(p);
        BigDecimal actual = c.actual();
        HealthResult h = evaluate(p, actual);

        BigDecimal total = p.getBudgetTotal();
        BigDecimal remaining = total == null ? null : total.subtract(actual);
        BigDecimal forecast = null;
        if (h.progress() >= 10 && actual.signum() > 0) {
            forecast = actual.multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(h.progress()), 2, RoundingMode.HALF_UP);
        }

        List<String> warnings = new ArrayList<>(c.warnings());
        if (total == null || total.signum() <= 0) warnings.add("No budget is set on this project.");
        if (p.getBaselinedAt() == null)
            warnings.add("No baseline saved yet. Save one to track schedule and budget variance.");

        ProjectFinanceResponse.Baseline baseline = null;
        if (p.getBaselinedAt() != null) {
            baseline = ProjectFinanceResponse.Baseline.builder()
                    .start(p.getBaselineStart()).end(p.getBaselineEnd()).budget(p.getBaselineBudget())
                    .at(p.getBaselinedAt()).byName(nameOf(p.getBaselinedBy()))
                    .currentStart(p.getStartDate()).currentEnd(p.getEndDate()).currentBudget(p.getBudgetTotal())
                    .startVarianceDays(days(p.getBaselineStart(), p.getStartDate()))
                    .endVarianceDays(days(p.getBaselineEnd(), p.getEndDate()))
                    .budgetVariance(p.getBaselineBudget() != null && p.getBudgetTotal() != null
                            ? p.getBudgetTotal().subtract(p.getBaselineBudget()) : null)
                    .build();
        }

        ProjectFinanceResponse.Health health = ProjectFinanceResponse.Health.builder()
                .value(p.getHealth() == null ? null : p.getHealth().name())
                .computed(h.level().name())
                .mode(p.getHealthMode() == null ? "AUTO" : p.getHealthMode().name())
                .tracked(TRACKED.contains(p.getStatus()))
                .reasons(h.reasons())
                .updatedAt(p.getHealthUpdatedAt())
                .build();

        return ProjectFinanceResponse.builder()
                .projectId(p.getId()).projectName(p.getName())
                .status(p.getStatus() == null ? null : p.getStatus().name())
                .budgetTotal(total).actualCost(actual).pendingCost(c.pending())
                .remaining(remaining).forecastAtCompletion(forecast)
                .burnPct(round1(h.burnPct())).progressPct(h.progress())
                .elapsedPct(round1(h.elapsedPct())).slipPct(round1(h.slipPct()))
                .openTasks(h.open()).overdueTasks(h.overdue()).blockedTasks(h.blocked())
                .criticalIssues(h.criticalIssues()).highRisks(h.highRisks())
                .baseline(baseline).health(health)
                .lines(c.lines()).warnings(warnings)
                .build();
    }

    // ═════════════════════════ write: recompute ═════════════════════════

    /** Recalculate cost + health of one project (used by the "Recompute now" button). */
    @Transactional
    public ProjectFinanceResponse recalculateProject(Long projectId, Long actorPk) {
        Project p = find(projectId);
        applyRecalc(p, actorPk);
        return finance(projectId);
    }

    /** Cost only: after a timesheet is approved/reopened or an hourly rate changes. */
    @Transactional
    public void recalculateCost(Long projectId) {
        projectRepository.findById(projectId).ifPresent(p -> {
            p.setBudgetSpent(computeCost(p).actual());
            projectRepository.save(p);
        });
    }

    /** After a timesheet week is approved or reopened: refresh every project that week touched. */
    @Transactional
    public void recalculateForEmployeeWeek(Long employeePk, LocalDate weekStart) {
        Set<Long> projectIds = new LinkedHashSet<>();
        for (TaskTimeLog l : timeLogRepository
                .findByEmployeeIdAndLogDateBetweenOrderByLogDateAsc(employeePk, weekStart, weekStart.plusDays(6))) {
            taskRepository.findById(l.getTaskId()).map(Task::getProjectId).ifPresent(projectIds::add);
        }
        projectIds.forEach(this::recalculateCost);
    }

    /** Switch a manually-set health back to automatic and recalculate now. */
    @Transactional
    public ProjectFinanceResponse resetToAuto(Long projectId, Long actorPk) {
        Project p = find(projectId);
        p.setHealthMode(HealthMode.AUTO);
        projectRepository.save(p);
        return recalculateProject(projectId, actorPk);
    }

    /** The "Recompute" button on the project list: projects the caller may manage. Returns {checked, changed}. */
    @Transactional
    public int[] recomputeAllFor(Predicate<Long> allowed, Long actorPk) {
        int checked = 0, changed = 0;
        for (Project p : projectRepository.findAll()) {
            if (!allowed.test(p.getId())) continue;
            checked++;
            if (applyRecalc(p, actorPk)) changed++;
        }
        return new int[]{checked, changed};
    }

    // ───── used by the schedulers (each call = its own transaction) ─────

    @Transactional(readOnly = true)
    public List<Long> allProjectIds() {
        return projectRepository.findAll().stream().map(Project::getId).collect(Collectors.toList());
    }

    /** @return true when the stored health changed */
    @Transactional
    public boolean recalculateOne(Long projectId) {
        return projectRepository.findById(projectId).map(p -> applyRecalc(p, null)).orElse(false);
    }

    /** Status update to the owner and managers. @return number of people notified. */
    @Transactional(readOnly = true)
    public int sendStatusReport(Long projectId, boolean force) {
        Project p = find(projectId);
        if (!force && !TRACKED.contains(p.getStatus())) return 0;

        CostResult c = computeCost(p);
        HealthResult h = evaluate(p, c.actual());
        StringBuilder msg = new StringBuilder();
        msg.append("Health: ").append(h.level())
                .append(" · Progress ").append(h.progress()).append("% (expected ")
                .append(Math.round(h.elapsedPct())).append("%)");
        if (p.getBudgetTotal() != null && p.getBudgetTotal().signum() > 0) {
            msg.append(" · Budget ").append(inr(c.actual())).append(" of ").append(inr(p.getBudgetTotal()))
                    .append(" (").append(Math.round(h.burnPct())).append("%)");
        }
        msg.append(" · ").append(h.overdue()).append(" overdue task(s)")
                .append(" · ").append(h.criticalIssues()).append(" critical issue(s)");
        if (!h.reasons().isEmpty() && h.level() != ProjectHealth.ON_TRACK)
            msg.append(" — ").append(String.join("; ", h.reasons()));

        return notifyPeople(p, "WEEKLY_STATUS", "📊 Status update: " + p.getName(), msg.toString(), "NORMAL");
    }

    // ═════════════════════════ core: one project ═════════════════════════

    /** Recalculate cost always; health only for tracked, automatic projects. @return true if health changed. */
    private boolean applyRecalc(Project p, Long actorPk) {
        CostResult c = computeCost(p);
        BigDecimal actual = c.actual();
        p.setBudgetSpent(actual);

        boolean changed = false;
        if (TRACKED.contains(p.getStatus()) && p.getHealthMode() != HealthMode.MANUAL) {
            HealthResult h = evaluate(p, actual);
            ProjectHealth old = p.getHealth();
            if (h.level() != old) {
                changed = true;
                p.setHealth(h.level());
                logHealthChange(p, old, h, actorPk);
                if (rank(h.level()) > rank(old) && h.level() != ProjectHealth.ON_TRACK) notifyWorse(p, old, h);
            }
            p.setHealthReason(truncate(String.join("; ", h.reasons()), 500));
            p.setHealthUpdatedAt(LocalDateTime.now());
        }
        projectRepository.save(p);
        return changed;
    }

    // ═════════════════════════ cost ═════════════════════════

    private CostResult computeCost(Project p) {
        Map<Long, ProjectMember> members = new HashMap<>();
        projectMemberRepository.findByProjectId(p.getId()).forEach(m -> members.put(m.getEmployeeId(), m));

        Map<Long, int[]> minutes = new LinkedHashMap<>();         // employee → [approved, pending]
        Map<String, Boolean> weekApproved = new HashMap<>();
        for (TaskTimeLog l : timeLogRepository.findForProject(p.getId())) {
            LocalDate ws = l.getLogDate().with(DayOfWeek.MONDAY);
            String key = l.getEmployeeId() + "|" + ws;
            boolean approved = weekApproved.computeIfAbsent(key, k ->
                    weekRepository.findByEmployeeIdAndWeekStart(l.getEmployeeId(), ws)
                            .map(w -> w.getStatus() == TimesheetStatus.APPROVED).orElse(false));
            minutes.computeIfAbsent(l.getEmployeeId(), k -> new int[2])[approved ? 0 : 1] += l.getMinutes();
        }

        BigDecimal approvedTotal = BigDecimal.ZERO, pendingTotal = BigDecimal.ZERO;
        List<ProjectFinanceResponse.CostLine> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (Map.Entry<Long, int[]> e : minutes.entrySet()) {
            Long empPk = e.getKey();
            int approvedMin = e.getValue()[0], pendingMin = e.getValue()[1];
            ProjectMember m = members.get(empPk);
            BigDecimal rate = m == null ? null : m.getHourlyRate();
            boolean noRate = rate == null || rate.signum() <= 0;

            BigDecimal approvedCost = noRate ? BigDecimal.ZERO : money(rate, approvedMin);
            BigDecimal pendingCost = noRate ? BigDecimal.ZERO : money(rate, pendingMin);
            approvedTotal = approvedTotal.add(approvedCost);
            pendingTotal = pendingTotal.add(pendingCost);

            String name = nameOf(empPk);
            if (noRate) {
                warnings.add(name + " logged " + hours(approvedMin + pendingMin) + " but "
                        + (m == null ? "is not a member of this project" : "has no hourly rate on this project")
                        + ", so that time is not costed.");
            }
            lines.add(ProjectFinanceResponse.CostLine.builder()
                    .employeeId(empPk).name(name).rate(rate)
                    .projectMember(m != null).rateMissing(noRate)
                    .approvedMinutes(approvedMin).pendingMinutes(pendingMin)
                    .approvedCost(approvedCost).pendingCost(pendingCost)
                    .build());
        }
        lines.sort(Comparator.comparing(ProjectFinanceResponse.CostLine::getApprovedCost).reversed());
        return new CostResult(approvedTotal, pendingTotal, lines, warnings);
    }

    // ═════════════════════════ health rules ═════════════════════════

    private HealthResult evaluate(Project p, BigDecimal actualCost) {
        LocalDate today = LocalDate.now();
        int progress = p.getProgressPct() == null ? 0 : p.getProgressPct();
        double elapsed = elapsedPct(p, today);
        double slip = elapsed - progress;
        double burn = (p.getBudgetTotal() != null && p.getBudgetTotal().signum() > 0)
                ? actualCost.doubleValue() / p.getBudgetTotal().doubleValue() : 0.0;

        List<Task> tasks = taskRepository.findByProjectId(p.getId());
        long open = tasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
        long overdue = tasks.stream().filter(t -> t.getStatus() != TaskStatus.DONE
                && t.getDueDate() != null && t.getDueDate().isBefore(today)).count();
        long blocked = tasks.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        double overdueRatio = open > 0 ? (double) overdue / open : 0.0;

        long criticalIssues = issueRepository.findByProjectIdOrderByCreatedAtDesc(p.getId()).stream()
                .filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity()) && !isClosed(i))
                .count();
        long highRisks = riskRepository.findByProjectIdOrderByScoreDesc(p.getId()).stream()
                .filter(r -> r.getScore() != null && r.getScore() >= 15 && isOpen(r))
                .count();

        Acc acc = new Acc();
        if (burn > BURN_OVER)
            acc.add(ProjectHealth.CRITICAL, "Over budget: " + Math.round(burn * 100) + "% of the budget is used");
        if (criticalIssues >= CRITICAL_ISSUES_CRITICAL)
            acc.add(ProjectHealth.CRITICAL, criticalIssues + " open critical issues");

        if (p.getEndDate() != null && p.getEndDate().isBefore(today) && progress < 100)
            acc.add(ProjectHealth.DELAYED, "Past the end date by "
                    + ChronoUnit.DAYS.between(p.getEndDate(), today) + " day(s) at " + progress + "% progress");
        if (slip > SLIP_DELAYED)
            acc.add(ProjectHealth.DELAYED, Math.round(slip) + " points behind schedule (time used "
                    + Math.round(elapsed) + "%, progress " + progress + "%)");

        if (slip > SLIP_AT_RISK && slip <= SLIP_DELAYED)
            acc.add(ProjectHealth.AT_RISK, Math.round(slip) + " points behind schedule (time used "
                    + Math.round(elapsed) + "%, progress " + progress + "%)");
        if (burn >= BURN_WARN && burn <= BURN_OVER && progress < BURN_WARN_MAX_PROGRESS)
            acc.add(ProjectHealth.AT_RISK, Math.round(burn * 100) + "% of the budget used but only "
                    + progress + "% done");
        if (overdueRatio > OVERDUE_RATIO_WARN)
            acc.add(ProjectHealth.AT_RISK, overdue + " of " + open + " open tasks are overdue");
        if (blocked >= BLOCKED_WARN)
            acc.add(ProjectHealth.AT_RISK, blocked + " tasks are blocked");
        if (criticalIssues >= CRITICAL_ISSUES_WARN && criticalIssues < CRITICAL_ISSUES_CRITICAL)
            acc.add(ProjectHealth.AT_RISK, criticalIssues + " open critical issue(s)");
        if (highRisks >= HIGH_RISKS_WARN)
            acc.add(ProjectHealth.AT_RISK, highRisks + " open high-severity risks");

        if (acc.reasons.isEmpty()) acc.reasons.add("On schedule and within budget");
        return new HealthResult(acc.level, acc.reasons, elapsed, slip, burn * 100.0, progress,
                open, overdue, blocked, criticalIssues, highRisks);
    }

    private static final class Acc {
        ProjectHealth level = ProjectHealth.ON_TRACK;
        final List<String> reasons = new ArrayList<>();
        void add(ProjectHealth l, String reason) {
            reasons.add(reason);
            if (rank(l) > rank(level)) level = l;
        }
    }

    /** CRITICAL > DELAYED > AT_RISK > ON_TRACK */
    private static int rank(ProjectHealth h) {
        if (h == null) return 0;
        return switch (h) {
            case ON_TRACK -> 0;
            case AT_RISK -> 1;
            case DELAYED -> 2;
            case CRITICAL -> 3;
        };
    }

    private double elapsedPct(Project p, LocalDate today) {
        if (p.getStartDate() == null || p.getEndDate() == null || !p.getEndDate().isAfter(p.getStartDate()))
            return 0.0;
        double total = ChronoUnit.DAYS.between(p.getStartDate(), p.getEndDate());
        double gone = ChronoUnit.DAYS.between(p.getStartDate(), today);
        return Math.max(0.0, Math.min(100.0, gone * 100.0 / total));
    }

    private boolean isClosed(Issue i) {
        return "RESOLVED".equalsIgnoreCase(i.getStatus()) || "CLOSED".equalsIgnoreCase(i.getStatus());
    }

    private boolean isOpen(Risk r) {
        return "OPEN".equalsIgnoreCase(r.getStatus()) || "MITIGATING".equalsIgnoreCase(r.getStatus());
    }

    // ═════════════════════════ notifications & log ═════════════════════════

    private void notifyWorse(Project p, ProjectHealth old, HealthResult h) {
        notifyPeople(p, "PROJECT_HEALTH",
                "⚠ " + p.getName() + " is now " + h.level().name().replace('_', ' '),
                String.join("; ", h.reasons()), h.level() == ProjectHealth.ON_TRACK ? "NORMAL" : "HIGH");
    }

    /** Owner + MANAGER members. */
    private int notifyPeople(Project p, String type, String title, String msg, String priority) {
        Set<Long> pks = new LinkedHashSet<>();
        if (p.getOwnerId() != null) pks.add(p.getOwnerId());
        projectMemberRepository.findByProjectId(p.getId()).stream()
                .filter(m -> m.getRole() == ProjectRole.MANAGER)
                .forEach(m -> pks.add(m.getEmployeeId()));
        int n = 0;
        for (Long pk : pks) {
            try {
                String code = employeeRepository.findById(pk).map(Employee::getEmployeeId).orElse(null);
                if (code == null) continue;
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type(type).action(type)
                        .title(title).message(msg)
                        .projectId(p.getId())
                        .priority(priority)
                        .deepLink("/pm/04-projects")
                        .build());
                n++;
            } catch (Exception ex) {
                log.warn("project notification failed for {}: {}", pk, ex.getMessage());
            }
        }
        return n;
    }

    private void logHealthChange(Project p, ProjectHealth old, HealthResult h, Long actorPk) {
        try {
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("PROJECT").entityId(p.getId()).action("HEALTH_CHANGED")
                    .actorId(actorPk).actorName(actorPk == null ? "System" : nameOf(actorPk))
                    .oldValue(old == null ? null : old.name()).newValue(h.level().name())
                    .message("health of " + p.getName() + " changed to " + h.level() + " — "
                            + String.join("; ", h.reasons()))
                    .build());
        } catch (Exception ex) {
            log.warn("could not log health change for project {}: {}", p.getId(), ex.getMessage());
        }
    }

    // ═════════════════════════ small helpers ═════════════════════════

    private Project find(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
    }

    private BigDecimal money(BigDecimal rate, int minutes) {
        return rate.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private String hours(int minutes) {
        int h = minutes / 60, r = minutes % 60;
        return h > 0 ? (r > 0 ? h + "h " + r + "m" : h + "h") : r + "m";
    }

    private Long days(LocalDate from, LocalDate to) {
        return (from == null || to == null) ? null : ChronoUnit.DAYS.between(from, to);
    }

    private String nameOf(Long pk) {
        if (pk == null) return null;
        return employeeRepository.findById(pk).map(e -> {
            String f = e.getFirstName() == null ? "" : e.getFirstName();
            String l = e.getLastName() == null ? "" : e.getLastName();
            String full = (f + " " + l).trim();
            return full.isEmpty() ? ("Employee #" + pk) : full;
        }).orElse("Employee #" + pk);
    }

    private String inr(BigDecimal v) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("en", "IN"));
        nf.setMaximumFractionDigits(0);
        return "₹" + nf.format(v == null ? BigDecimal.ZERO : v);
    }

    private String truncate(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }

    private double round1(double x) { return Math.round(x * 10.0) / 10.0; }
}