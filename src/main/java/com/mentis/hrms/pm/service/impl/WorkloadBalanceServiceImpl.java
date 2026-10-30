package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.pm.balance.WorkloadBalancePlanner;
import com.mentis.hrms.pm.balance.WorkloadBalancePlanner.Item;
import com.mentis.hrms.pm.balance.WorkloadBalancePlanner.Person;
import com.mentis.hrms.pm.balance.WorkloadBalancePlanner.Result;
import com.mentis.hrms.pm.dto.request.BalanceApplyRequest;
import com.mentis.hrms.pm.dto.request.BalancePreviewRequest;
import com.mentis.hrms.pm.dto.response.BalanceApplyResponse;
import com.mentis.hrms.pm.dto.response.BalancePreviewResponse;
import com.mentis.hrms.pm.dto.response.WorkloadResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.HandoverService;
import com.mentis.hrms.pm.service.PmParticipantService;
import com.mentis.hrms.pm.service.TaskEventNotifier;
import com.mentis.hrms.pm.service.WorkloadBalanceService;
import com.mentis.hrms.pm.service.WorkloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GAP 19c - workload auto-balancing. SUGGEST FIRST, NEVER SILENT:
 *  - preview() is read-only and returns a proposal.
 *  - apply() changes only the moves the user ticked, goes through HandoverService.moveOne
 *    (the same assignment + ActivityLog path as a handover) and sends ONE summary bell per
 *    recipient after the transaction commits.
 *
 * Tunables (application.properties):
 *   pm.balance.max-moves        = 20   hard cap on proposed moves
 *   pm.balance.target-cap-pct   = 85   a receiver may never go above this after a move
 *   pm.balance.source-stop-pct  = 100  stop moving from someone once they are at/below this
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkloadBalanceServiceImpl implements WorkloadBalanceService {

    private static final int MAX_WINDOW_DAYS = 62;
    /** reuse the existing bell type so the notification UI already knows how to render it */
    private static final String BELL_TYPE = "TASK_HANDED_OVER";

    private final WorkloadService workload;
    private final HandoverService handover;
    private final PmParticipantService participants;
    private final PmAccessService access;
    private final TaskRepository taskRepo;
    private final TaskAssignmentRepository assignmentRepo;
    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final TeamMemberRepository teamMemberRepo;
    private final TaskEventNotifier notifier;

    @Value("${pm.balance.max-moves:20}")
    private int maxMoves;
    @Value("${pm.balance.target-cap-pct:85}")
    private int targetCapPct;
    @Value("${pm.balance.source-stop-pct:100}")
    private int sourceStopPct;

    // ═════════════════════════════════════════════════════════ preview

    @Override
    @Transactional(readOnly = true)
    public BalancePreviewResponse preview(BalancePreviewRequest req, Long actorPk) {
        LocalDate from = req.getFrom() != null ? req.getFrom() : LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate to = req.getTo() != null ? req.getTo() : from.plusDays(6);
        validateWindow(from, to);

        List<WorkloadResponse> loads = loadPeople(req, from, to);

        Map<Long, Task> taskCache = new HashMap<>();
        Map<Long, Set<Long>> assigneeCache = new HashMap<>();
        Map<String, Boolean> canMoveCache = new HashMap<>();
        Map<Long, String> projectNames = new HashMap<>();

        List<Person> persons = new ArrayList<>();
        Map<Long, Person> personByPk = new LinkedHashMap<>();
        for (WorkloadResponse w : loads) {
            if (w.getEmployeePk() == null) continue;
            Person p = new Person(w.getEmployeePk(), w.getEmployeeName(), w.getCapacityHours(),
                    w.getAllocatedHours(), w.getLeaveDays() > 0, w.getStatus());
            persons.add(p);
            personByPk.put(p.pk(), p);
        }

        // 1. movable tasks of the OVER people (open, NOT started, project not locked, actor allowed)
        List<Item> items = new ArrayList<>();
        Map<Long, Integer> movableBySource = new HashMap<>();
        int overCount = 0;
        for (WorkloadResponse w : loads) {
            if (!"OVER".equals(w.getStatus())) continue;
            overCount++;
            if (w.getTasks() == null) continue;
            for (WorkloadResponse.TaskBreakdown tb : w.getTasks()) {
                Task t = taskCache.computeIfAbsent(tb.getTaskId(), id -> taskRepo.findById(id).orElse(null));
                if (t == null || !isNotStarted(t)) continue;
                // in project mode only propose moves for THAT project's tasks
                if (req.getProjectId() != null && !req.getProjectId().equals(t.getProjectId())) continue;
                if (access.isProjectLocked(t.getProjectId())) continue;
                if (!canMove(actorPk, t, canMoveCache)) continue;
                double h = hoursOf(t);
                if (h <= 0) continue;
                items.add(new Item(t.getId(), w.getEmployeePk(), h));
                movableBySource.merge(w.getEmployeePk(), 1, Integer::sum);
            }
        }

        // 2. receivers must be real participants of the task's project and not already on it
        WorkloadBalancePlanner.Eligibility eligible = (item, toPk) -> {
            Task t = taskCache.get(item.taskId());
            if (t == null) return false;
            if (assigneesOf(t.getId(), assigneeCache).contains(toPk)) return false;
            return participants.isParticipant(t, toPk);
        };

        Result result = WorkloadBalancePlanner.plan(persons, items, eligible,
                targetCapPct / 100.0, sourceStopPct / 100.0, maxMoves);

        // 3. turn the plan into rows the UI can show
        List<BalancePreviewResponse.Move> rows = new ArrayList<>();
        Map<Long, BalancePreviewResponse.PersonDelta> touched = new LinkedHashMap<>();
        for (WorkloadBalancePlanner.Move m : result.moves()) {
            Task t = taskCache.get(m.item().taskId());
            Person src = personByPk.get(m.fromPk());
            Person dst = personByPk.get(m.toPk());
            int fb = pct(m.fromBeforePct()), fa = pct(m.fromAfterPct());
            int tb = pct(m.toBeforePct()), ta = pct(m.toAfterPct());
            rows.add(BalancePreviewResponse.Move.builder()
                    .taskId(t.getId()).title(t.getTitle())
                    .projectId(t.getProjectId()).projectName(projectName(t.getProjectId(), projectNames))
                    .hours(m.item().hours())
                    .fromEmployeeId(src.pk()).fromName(src.name())
                    .toEmployeeId(dst.pk()).toName(dst.name())
                    .fromBeforePct(fb).fromAfterPct(fa).toBeforePct(tb).toAfterPct(ta)
                    .reason(src.name() + " is at " + fb + "% of capacity. " + dst.name()
                            + " has the most room among eligible teammates (" + tb + "% to " + ta
                            + "%, staying under " + targetCapPct + "%).")
                    .build());
            touched.computeIfAbsent(src.pk(), k -> delta(src, result));
            touched.computeIfAbsent(dst.pk(), k -> delta(dst, result));
        }

        // 4. explain anyone who is still over so the user is not left guessing
        List<String> notes = new ArrayList<>();
        if (overCount == 0) {
            notes.add("Nobody is over capacity in this period, so there is nothing to balance.");
        }
        for (Person p : persons) {
            if (!"OVER".equals(p.status())) continue;
            double now = result.finalAllocated().get(p.pk());
            if (now <= p.capacityHours() * sourceStopPct / 100.0 + 1e-9) continue;
            int still = pct(WorkloadBalancePlanner.util(now, p.capacityHours()));
            if (movableBySource.getOrDefault(p.pk(), 0) == 0) {
                notes.add(p.name() + " is still at " + still + "%: no not-started, unlocked task that you are allowed to move.");
            } else if (result.capped()) {
                notes.add(p.name() + " is still at " + still + "%: the proposal was capped at " + maxMoves + " moves.");
            } else {
                notes.add(p.name() + " is still at " + still + "%: nobody eligible has room under " + targetCapPct + "%.");
            }
        }
        if (req.getProjectId() != null) {
            notes.add("Project view: only tasks of this project are proposed, but utilisation counts all of a person's work.");
        }

        return BalancePreviewResponse.builder()
                .from(from).to(to).overCount(overCount)
                .maxMoves(maxMoves).capped(result.capped())
                .moves(rows).people(new ArrayList<>(touched.values())).notes(notes)
                .build();
    }

    // ═════════════════════════════════════════════════════════ apply

    @Override
    @Transactional
    public BalanceApplyResponse apply(BalanceApplyRequest req, Long actorPk) {
        LocalDate from = req.getFrom() != null ? req.getFrom() : LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate to = req.getTo() != null ? req.getTo() : from.plusDays(6);
        validateWindow(from, to);

        final double cap = targetCapPct / 100.0;
        String actorName = notifier.nameOf(actorPk);
        List<String> skipped = new ArrayList<>();
        List<BalanceApplyResponse.Applied> moved = new ArrayList<>();
        Map<Long, Integer> perRecipient = new LinkedHashMap<>();
        Map<Long, Double> hoursInto = new HashMap<>();
        Map<String, Boolean> canMoveCache = new HashMap<>();

        // Snapshot every receiver's load BEFORE the first write, so the 85% re-check does not
        // depend on when Hibernate flushes (and nothing is double counted).
        Map<Long, WorkloadResponse> snapshot = new HashMap<>();
        for (BalanceApplyRequest.Move m : req.getMoves()) {
            snapshot.computeIfAbsent(m.getToEmployeeId(), id -> workload.forEmployee(id, from, to));
        }

        Set<Long> seenTasks = new HashSet<>();
        for (BalanceApplyRequest.Move m : req.getMoves()) {
            if (!seenTasks.add(m.getTaskId())) {
                skipped.add("Task #" + m.getTaskId() + " was listed twice; only the first move was used.");
                continue;
            }
            Task t = taskRepo.findById(m.getTaskId()).orElse(null);
            if (t == null) {
                skipped.add("Task #" + m.getTaskId() + " no longer exists.");
                continue;
            }
            String label = "\"" + t.getTitle() + "\": ";

            // the preview only ever proposes not-started work; re-check, it may have changed since
            if (!isNotStarted(t)) {
                skipped.add(label + "work has started or the task is no longer open, so it was not moved.");
                continue;
            }
            if (!canMove(actorPk, t, canMoveCache)) {
                skipped.add(label + "you do not have rights to reassign tasks in this project.");
                continue;
            }
            Long fromId = resolveFrom(t, m);
            if (fromId == null) {
                skipped.add(label + "it is no longer assigned to the person shown in the preview.");
                continue;
            }
            Long toId = m.getToEmployeeId();
            WorkloadResponse target = snapshot.get(toId);
            if (target == null) {
                skipped.add(label + "the receiving person was not found.");
                continue;
            }
            if (target.getLeaveDays() > 0) {
                skipped.add(label + target.getEmployeeName() + " now has approved leave in this period.");
                continue;
            }
            double h = hoursOf(t);
            double projected = target.getAllocatedHours() + hoursInto.getOrDefault(toId, 0.0) + h;
            if (target.getCapacityHours() <= 0 || projected > target.getCapacityHours() * cap + 1e-9) {
                skipped.add(label + target.getEmployeeName() + " would go over " + targetCapPct + "% of capacity.");
                continue;
            }

            // same reassign path as a handover: assignment + ActivityLog, project lock + participant checks
            String why = handover.moveOne(t.getId(), fromId, toId, actorPk, "Workload balance");
            if (why != null) {
                skipped.add(why);
                continue;
            }
            hoursInto.merge(toId, h, Double::sum);
            perRecipient.merge(toId, 1, Integer::sum);
            moved.add(BalanceApplyResponse.Applied.builder()
                    .taskId(t.getId()).title(t.getTitle())
                    .fromName(notifier.nameOf(fromId)).toName(target.getEmployeeName())
                    .build());
        }

        // ONE summary bell per recipient, sent only after the transaction commits
        List<NotificationRequest> bells = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : perRecipient.entrySet()) {
            if (e.getKey().equals(actorPk)) continue;          // the person who clicked is not notified
            String code = notifier.codeOf(e.getKey());
            if (code == null) continue;
            int n = e.getValue();
            bells.add(notifier.build(code, BELL_TYPE,
                    n + " task(s) assigned to you",
                    actorName + " rebalanced the team's workload and assigned " + n + " not-started task(s) to you",
                    "NORMAL", null, null, "/pm/03-my-work", actorPk, actorName));
        }
        sendAfterCommit(bells);

        log.info("workload balance by {}: {} applied, {} skipped", actorPk, moved.size(), skipped.size());
        return BalanceApplyResponse.builder().applied(moved.size()).moved(moved).skipped(skipped).build();
    }

    // ═════════════════════════════════════════════════════════ helpers

    private List<WorkloadResponse> loadPeople(BalancePreviewRequest req, LocalDate from, LocalDate to) {
        if (req.getTeamId() != null) return workload.forTeam(req.getTeamId(), from, to);

        Long pid = req.getProjectId();
        Project p = projectRepo.findById(pid)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + pid));
        // forProject() only lists people who already hold a task; receivers may have none yet,
        // so the pool is: members (not viewers) + the project's team + current assignees.
        Set<Long> pool = new LinkedHashSet<>();
        for (ProjectMember m : memberRepo.findByProjectId(pid)) {
            if (m.getRole() != ProjectRole.VIEWER) pool.add(m.getEmployeeId());
        }
        if (p.getTeamId() != null) {
            teamMemberRepo.findByTeamId(p.getTeamId()).forEach(tm -> pool.add(tm.getEmployeeId()));
        }
        for (Task t : taskRepo.findByProjectId(pid)) {
            for (TaskAssignment a : assignmentRepo.findByTaskId(t.getId())) pool.add(a.getEmployeeId());
        }
        return workload.forEmployees(new ArrayList<>(pool), from, to);
    }

    private void validateWindow(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new BusinessRuleException("The end date must not be before the start date");
        if (ChronoUnit.DAYS.between(from, to) > MAX_WINDOW_DAYS)
            throw new BusinessRuleException("Pick a period of at most " + MAX_WINDOW_DAYS + " days");
    }

    /** "not started": TODO, 0% progress, nothing logged. Never DONE, BLOCKED, in progress or in review. */
    private boolean isNotStarted(Task t) {
        return t.getStatus() == TaskStatus.TODO
                && (t.getProgressPct() == null || t.getProgressPct() == 0)
                && (t.getLoggedMinutes() == null || t.getLoggedMinutes() == 0);
    }

    /** exactly how WorkloadServiceImpl counts a task: estimate, else story points * 4, else 0 */
    private double hoursOf(Task t) {
        if (t.getEstimateHours() != null) return t.getEstimateHours();
        return t.getStoryPoints() != null ? t.getStoryPoints() * 4.0 : 0.0;
    }

    private Set<Long> assigneesOf(Long taskId, Map<Long, Set<Long>> cache) {
        return cache.computeIfAbsent(taskId, id -> {
            Set<Long> s = new HashSet<>();
            for (TaskAssignment a : assignmentRepo.findByTaskId(id)) s.add(a.getEmployeeId());
            return s;
        });
    }

    /** single assignee -> that person; several assignees -> the request must say which one. */
    private Long resolveFrom(Task t, BalanceApplyRequest.Move m) {
        List<TaskAssignment> as = assignmentRepo.findByTaskId(t.getId());
        if (m.getFromEmployeeId() != null) {
            return as.stream().anyMatch(a -> m.getFromEmployeeId().equals(a.getEmployeeId()))
                    ? m.getFromEmployeeId() : null;
        }
        return as.size() == 1 ? as.get(0).getEmployeeId() : null;
    }

    /**
     * Who may move work: a Super Admin, someone who can administer the task's project
     * (owner / project manager, the same rule handover uses), or the lead of the task's team.
     * HR is deliberately not included (read-only on projects).
     */
    private boolean canMove(Long actorPk, Task t, Map<String, Boolean> cache) {
        String key = t.getProjectId() + ":" + t.getTeamId();
        return cache.computeIfAbsent(key, k -> {
            if (access.isSuperAdmin(actorPk)) return true;
            try {
                access.requireCanAdminister(actorPk, t.getProjectId());
                return true;
            } catch (RuntimeException denied) {
                // not an administrator of the project: fall through to the team-lead check
            }
            Long teamId = t.getTeamId() != null ? t.getTeamId()
                    : projectRepo.findById(t.getProjectId()).map(Project::getTeamId).orElse(null);
            return teamId != null && access.isTeamLead(actorPk, teamId);
        });
    }

    private String projectName(Long pid, Map<Long, String> cache) {
        if (pid == null) return "No project";
        return cache.computeIfAbsent(pid, k -> projectRepo.findById(k).map(Project::getName).orElse("Project #" + k));
    }

    private BalancePreviewResponse.PersonDelta delta(Person p, Result r) {
        return BalancePreviewResponse.PersonDelta.builder()
                .employeeId(p.pk()).name(p.name())
                .beforePct(pct(WorkloadBalancePlanner.util(p.allocatedHours(), p.capacityHours())))
                .afterPct(pct(WorkloadBalancePlanner.util(r.finalAllocated().get(p.pk()), p.capacityHours())))
                .build();
    }

    private int pct(double v) { return (int) Math.round(v); }

    private void sendAfterCommit(List<NotificationRequest> bells) {
        if (bells.isEmpty()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { notifier.sendAll(bells); }
            });
        } else {
            notifier.sendAll(bells);
        }
    }
}