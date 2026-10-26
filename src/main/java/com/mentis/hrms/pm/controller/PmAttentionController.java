package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.MyWorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * STEP 7.0b: data for the role-aware UI.
 *   GET /api/pm/me/review-queue         tasks In Review that I may review
 *   GET /api/pm/dashboard/attention     what needs a lead's / PM's attention right now
 *
 * Read-only. Scope is derived from the session user, never from a request parameter.
 */
@RestController("pmAttentionController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class PmAttentionController {

    private static final int TOP = 5;

    private final MyWorkService myWorkService;
    private final PmAccessService access;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamRepository teamRepository;
    private final TaskRepository taskRepository;

    // ─── response shapes ───

    public record AttentionTask(Long id, String title, Long projectId, String projectName,
                                LocalDate dueDate, String status) {}

    public record AttentionProject(Long id, String code, String name, String health, Integer progressPct) {}

    public record Attention(long reviewWaiting, long blocked, long overdue, long atRiskProjects,
                            List<AttentionTask> reviewItems,
                            List<AttentionTask> blockedItems,
                            List<AttentionTask> overdueItems,
                            List<AttentionProject> riskItems) {}

    // ─── endpoints ───

    @GetMapping("/me/review-queue")
    public ApiResponse<List<TaskSummaryResponse>> reviewQueue(@CurrentUserId Long userId) {
        return ApiResponse.ok(myWorkService.listReviewQueue(userId));
    }

    @GetMapping("/dashboard/attention")
    public ApiResponse<Attention> attention(@CurrentUserId Long userId) {
        List<Project> led = ledProjects(userId);
        if (led.isEmpty()) {
            return ApiResponse.ok(new Attention(0, 0, 0, 0, List.of(), List.of(), List.of(), List.of()));
        }

        Set<Long> ids = led.stream().map(Project::getId).collect(Collectors.toSet());
        Map<Long, String> names = led.stream()
                .collect(Collectors.toMap(Project::getId, p -> p.getName() == null ? "" : p.getName(), (a, b) -> a));

        // waiting for review: only tasks this person may actually review
        List<Task> review = taskRepository.findByStatus(TaskStatus.IN_REVIEW).stream()
                .filter(t -> t.getProjectId() != null && ids.contains(t.getProjectId()))
                .filter(t -> access.canReviewTask(userId, t.getId()))
                .sorted(Comparator.comparing(Task::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // blocked, longest-blocked first
        List<Task> blocked = taskRepository.findByStatus(TaskStatus.BLOCKED).stream()
                .filter(t -> t.getProjectId() != null && ids.contains(t.getProjectId()))
                .sorted(Comparator.comparing(Task::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // overdue (existing query is already ordered by due date, oldest first)
        List<Task> overdue = taskRepository.findOverdueInProjects(ids, LocalDate.now(), TaskStatus.DONE);

        // projects that are slipping
        List<Project> risky = led.stream()
                .filter(p -> {
                    String h = p.getHealth() == null ? "" : p.getHealth().name();
                    return h.equals("AT_RISK") || h.equals("DELAYED") || h.equals("CRITICAL");
                })
                .toList();

        return ApiResponse.ok(new Attention(
                review.size(), blocked.size(), overdue.size(), risky.size(),
                toItems(review, names), toItems(blocked, names), toItems(overdue, names),
                risky.stream().limit(TOP)
                        .map(p -> new AttentionProject(p.getId(), p.getCode(), p.getName(),
                                p.getHealth() == null ? null : p.getHealth().name(), p.getProgressPct()))
                        .toList()));
    }

    // ─── helpers ───

    private List<AttentionTask> toItems(List<Task> tasks, Map<Long, String> names) {
        return tasks.stream().limit(TOP)
                .map(t -> new AttentionTask(t.getId(), t.getTitle(), t.getProjectId(),
                        names.get(t.getProjectId()), t.getDueDate(),
                        t.getStatus() == null ? null : t.getStatus().name()))
                .toList();
    }

    /**
     * Active projects this person leads or administers.
     *   Super Admin               : every active project
     *   owner / MANAGER           : managedProjectIds
     *   explicit LEAD role        : projects where they hold LEAD
     *   lead of the project's team: projects of the teams they lead
     * HR gets none (HR has the company view, not the lead view).
     */
    private List<Project> ledProjects(Long userId) {
        if (userId == null) return List.of();

        List<Project> candidates;
        if (access.isSuperAdmin(userId)) {
            candidates = projectRepository.findAll();
        } else {
            Set<Long> ids = new HashSet<>(access.managedProjectIds(userId));

            projectMemberRepository.findByEmployeeId(userId).stream()
                    .filter(m -> m.getRole() == ProjectRole.LEAD)
                    .forEach(m -> ids.add(m.getProjectId()));

            Set<Long> ledTeams = teamRepository.findByLeadEmployeeId(userId).stream()
                    .map(t -> t.getId())
                    .collect(Collectors.toSet());
            if (!ledTeams.isEmpty()) {
                projectRepository.findAll().stream()
                        .filter(p -> p.getTeamId() != null && ledTeams.contains(p.getTeamId()))
                        .forEach(p -> ids.add(p.getId()));
            }
            if (ids.isEmpty()) return List.of();
            candidates = projectRepository.findAllById(ids);
        }

        return candidates.stream()
                .filter(p -> p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.ARCHIVED)
                .toList();
    }
}