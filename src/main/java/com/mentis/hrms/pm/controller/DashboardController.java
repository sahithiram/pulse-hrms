package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ActivityResponse;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ChartDataResponse;
import com.mentis.hrms.pm.dto.response.DashboardKpiResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.entity.Client;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.mapper.ProjectMapper;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ClientRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


@RestController("pmDashboardController")
@RequestMapping("/api/pm/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ProjectMapper projectMapper;
    private final ClientRepository clientRepository;
    private final PmAccessService access;

    /** projects the user may see, paged */
    private List<Project> visibleProjects(Long userId, Pageable pageable) {
        return projectRepository.searchVisible(null, null, null, userId, pageable).getContent();
    }

    private long visibleCount(Long userId, ProjectStatus status) {
        return projectRepository.searchVisible(null, status, null, userId, PageRequest.of(0, 1))
                .getTotalElements();
    }

    /** tasks assigned to the user */
    private List<Task> myTasks(Long userId) {
        List<Long> ids = taskAssignmentRepository.findByEmployeeId(userId).stream()
                .map(TaskAssignment::getTaskId).distinct().collect(Collectors.toList());
        return taskRepository.findAllById(ids);
    }

    @GetMapping("/kpis")
    public ApiResponse<DashboardKpiResponse> kpis(@CurrentUserId Long userId) {
        if (access.isAdmin(userId)) {
            return ApiResponse.ok(DashboardKpiResponse.builder()
                    .activeProjects(projectRepository.countByStatus(ProjectStatus.ACTIVE))
                    .completedProjects(projectRepository.countByStatus(ProjectStatus.COMPLETED))
                    .atRiskProjects(projectRepository.countByStatus(ProjectStatus.AT_RISK))
                    .openTasks(taskRepository.countByStatusNot(TaskStatus.DONE))
                    .completedTasks(taskRepository.countByStatus(TaskStatus.DONE))
                    .totalProjects(projectRepository.count())
                    .totalTasks(taskRepository.count())
                    .build());
        }
        if (access.canCreateProject(userId)) {          // PM: numbers for ALL projects I can see
            List<Long> ids = visibleProjectIds(userId);
            long open = ids.isEmpty() ? 0 : taskRepository.countOpenInProjects(ids, TaskStatus.DONE);
            long done = ids.isEmpty() ? 0 : taskRepository.countDoneInProjects(ids, TaskStatus.DONE);
            return ApiResponse.ok(DashboardKpiResponse.builder()
                    .activeProjects(visibleCount(userId, ProjectStatus.ACTIVE))
                    .completedProjects(visibleCount(userId, ProjectStatus.COMPLETED))
                    .atRiskProjects(visibleCount(userId, ProjectStatus.AT_RISK))
                    .openTasks(open).completedTasks(done)
                    .totalProjects(visibleCount(userId, null))
                    .totalTasks(open + done)
                    .build());
        }
        List<Task> mine = myTasks(userId);
        long done = mine.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        return ApiResponse.ok(DashboardKpiResponse.builder()
                .activeProjects(visibleCount(userId, ProjectStatus.ACTIVE))
                .completedProjects(visibleCount(userId, ProjectStatus.COMPLETED))
                .atRiskProjects(visibleCount(userId, ProjectStatus.AT_RISK))
                .openTasks(mine.size() - done)
                .completedTasks(done)
                .totalProjects(visibleCount(userId, null))
                .totalTasks((long) mine.size())
                .build());
    }

    @GetMapping("/charts/task-distribution")
    public ApiResponse<ChartDataResponse> taskDistribution(@CurrentUserId Long userId) {
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        if (access.isAdmin(userId)) {
            for (Object[] row : taskRepository.countGroupByStatus()) {
                labels.add(((TaskStatus) row[0]).name());
                data.add(((Number) row[1]).longValue());
            }
        } else if (access.canCreateProject(userId)) {
            List<Long> ids = visibleProjectIds(userId);
            if (!ids.isEmpty()) {
                for (Object[] row : taskRepository.countGroupByStatusInProjects(ids)) {
                    labels.add(((TaskStatus) row[0]).name());
                    data.add(((Number) row[1]).longValue());
                }
            }
        } else {
            Map<TaskStatus, Long> grouped = myTasks(userId).stream()
                    .filter(t -> t.getStatus() != null)
                    .collect(Collectors.groupingBy(Task::getStatus, Collectors.counting()));
            grouped.forEach((s, c) -> { labels.add(s.name()); data.add(c); });
        }
        return ApiResponse.ok(ChartDataResponse.builder().labels(labels).data(data).build());
    }

    @GetMapping("/charts/project-progress")
    public ApiResponse<ChartDataResponse> projectProgress(@CurrentUserId Long userId) {
        PageRequest pr = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Project> projects = access.isAdmin(userId)
                ? projectRepository.findAll(pr).getContent()
                : visibleProjects(userId, pr);
        List<String> labels = new ArrayList<>();
        List<Long> data = new ArrayList<>();
        for (Project p : projects) {
            String name = p.getName();
            labels.add(name.length() > 12 ? name.substring(0, 12) + "…" : name);
            data.add((long) (p.getProgressPct() == null ? 0 : p.getProgressPct()));
        }
        return ApiResponse.ok(ChartDataResponse.builder().labels(labels).data(data).build());
    }

    @GetMapping("/recent-projects")
    public ApiResponse<List<ProjectSummaryResponse>> recentProjects(
            @RequestParam(defaultValue = "5") int limit,
            @CurrentUserId Long userId) {
        PageRequest pr = PageRequest.of(0, Math.min(Math.max(limit, 1), 50),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<Project> projects = access.isAdmin(userId)
                ? projectRepository.findAll(pr).getContent()
                : visibleProjects(userId, pr);

        List<Long> clientIds = projects.stream()
                .map(Project::getClientId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, String> clientNameMap = clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(
                        Client::getId,
                        c -> c.getName() == null ? "" : c.getName(),
                        (existing, replacement) -> existing
                ));

        List<ProjectSummaryResponse> list = projects.stream()
                .map(p -> projectMapper.toSummary(p, clientNameMap.get(p.getClientId())))
                .toList();
        return ApiResponse.ok(list);
    }

    @GetMapping("/recent-activity")
    public ApiResponse<List<ActivityResponse>> recentActivity(
            @RequestParam(defaultValue = "10") int limit,
            @CurrentUserId Long userId) {
        boolean admin = access.isAdmin(userId);
        Set<Long> myProjects = admin ? Set.of() : new HashSet<>(visibleProjectIds(userId));
        var logs = activityLogRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .filter(a -> admin
                        || Objects.equals(a.getActorId(), userId)
                        || ("PROJECT".equals(a.getEntityType()) && myProjects.contains(a.getEntityId())))
                .toList();
        List<ActivityResponse> list = logs.stream()
                .limit(limit)
                .map(a -> ActivityResponse.builder()
                        .id(a.getId())
                        .entityType(a.getEntityType())
                        .entityId(a.getEntityId())
                        .action(a.getAction())
                        .actorId(a.getActorId())
                        .actorName(a.getActorName())
                        .oldValue(a.getOldValue())
                        .newValue(a.getNewValue())
                        .message(a.getMessage())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();
        return ApiResponse.ok(list);
    }
    /** ids of every project the user can see; used to scope task numbers for PMs */
    private List<Long> visibleProjectIds(Long userId) {
        return projectRepository.searchVisible(null, null, null, userId, PageRequest.of(0, 500))
                .getContent().stream().map(Project::getId).toList();
    }
    @GetMapping("/overdue")
    public ApiResponse<List<Map<String, Object>>> overdue(
            @RequestParam(defaultValue = "8") int limit, @CurrentUserId Long userId) {
        List<Long> ids = access.isAdmin(userId)
                ? projectRepository.findAll().stream().map(Project::getId).toList()
                : visibleProjectIds(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        if (ids.isEmpty()) return ApiResponse.ok(out);
        taskRepository.findOverdueInProjects(ids, LocalDate.now(), TaskStatus.DONE).stream()
                .limit(Math.min(Math.max(limit, 1), 50))
                .forEach(t -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", t.getId());
                    m.put("title", t.getTitle());
                    m.put("dueDate", t.getDueDate());
                    m.put("projectId", t.getProjectId());
                    m.put("status", t.getStatus());
                    out.add(m);
                });
        return ApiResponse.ok(out);
    }

    @GetMapping("/team-workload")
    public ApiResponse<List<Map<String, Object>>> teamWorkload(
            @RequestParam(defaultValue = "8") int limit,
            @CurrentUserId Long userId) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!access.isAdmin(userId)) return ApiResponse.ok(result);   // everyone's workload: admin only

        var allAssignments = taskAssignmentRepository.findAll();
        Map<Long, Long> counts = new HashMap<>();
        for (var ta : allAssignments) {
            counts.merge(ta.getEmployeeId(), 1L, Long::sum);
        }
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("employeeId", e.getKey());
                    m.put("name", "User #" + e.getKey());
                    m.put("openTasks", e.getValue());
                    m.put("loadPct", Math.min(100, e.getValue() * 15));
                    result.add(m);
                });
        return ApiResponse.ok(result);
    }
}