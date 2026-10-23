package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.entity.TeamMember;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.mapper.ProjectMapper;
import com.mentis.hrms.pm.mapper.TaskMapper;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.MyWorkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MyWorkServiceImpl implements MyWorkService {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskWatcherRepository taskWatcherRepository;
    private final TaskTimeLogRepository taskTimeLogRepository;
    private final ProjectRepository projectRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectMapper projectMapper;
    private final TaskMapper taskMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> listMyTasks(Long userId) {
        if (userId == null) return List.of();

        // 1) task ids from assignment + watcher
        Set<Long> taskIds = new LinkedHashSet<>();
        taskAssignmentRepository.findByEmployeeId(userId)
                .forEach(a -> taskIds.add(a.getTaskId()));
        taskWatcherRepository.findByEmployeeId(userId)
                .forEach(w -> taskIds.add(w.getTaskId()));

        if (taskIds.isEmpty()) return List.of();

        // 2) load and map
        List<Task> tasks = taskRepository.findAllById(taskIds);

        // 3) hide subtasks from the list — they show inside their parent drawer
        tasks.removeIf(t -> t.getParentTaskId() != null);

        // 4) order: open first, then due date asc, then created desc
        tasks.sort((a, b) -> {
            boolean aDone = a.getStatus() == TaskStatus.DONE;
            boolean bDone = b.getStatus() == TaskStatus.DONE;
            if (aDone != bDone) return aDone ? 1 : -1;
            if (a.getDueDate() != null && b.getDueDate() != null) {
                int c = a.getDueDate().compareTo(b.getDueDate());
                if (c != 0) return c;
            }
            if (a.getCreatedAt() != null && b.getCreatedAt() != null)
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            return 0;
        });

        return tasks.stream().map(this::toSummary).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> listMyProjects(Long userId) {
        if (userId == null) return List.of();

        Set<Long> projectIds = new LinkedHashSet<>();

        // 1) projects I own
        projectRepository.findByOwnerId(userId)
                .forEach(p -> projectIds.add(p.getId()));

        // 2) projects whose team I'm a member of
        List<Long> myTeamIds = teamMemberRepository.findByEmployeeId(userId)
                .stream().map(TeamMember::getTeamId).toList();
        if (!myTeamIds.isEmpty()) {
            projectRepository.findAll().stream()
                    .filter(p -> p.getTeamId() != null && myTeamIds.contains(p.getTeamId()))
                    .forEach(p -> projectIds.add(p.getId()));
        }

        // 3) projects where I have at least one task
        for (TaskAssignment a : taskAssignmentRepository.findByEmployeeId(userId)) {
            taskRepository.findById(a.getTaskId())
                    .map(Task::getProjectId)
                    .ifPresent(projectIds::add);
        }

        // 4) projects where I am an explicit project member
        projectMemberRepository.findByEmployeeId(userId)
                .forEach(m -> projectIds.add(m.getProjectId()));

        if (projectIds.isEmpty()) return List.of();

        return projectRepository.findAllById(projectIds).stream()
                .map(p -> projectMapper.toSummary(p, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public MyWorkKpi getKpi(Long userId) {
        if (userId == null)
            return new MyWorkKpi(0, 0, 0, 0, 0);

        // Gather task ids once
        Set<Long> taskIds = new LinkedHashSet<>();
        taskAssignmentRepository.findByEmployeeId(userId)
                .forEach(a -> taskIds.add(a.getTaskId()));
        taskWatcherRepository.findByEmployeeId(userId)
                .forEach(w -> taskIds.add(w.getTaskId()));

        if (taskIds.isEmpty())
            return new MyWorkKpi(0, 0, 0, 0, 0);

        List<Task> tasks = taskRepository.findAllById(taskIds).stream()
                .filter(t -> t.getParentTaskId() == null)
                .toList();

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        long open = 0, dueToday = 0, overdue = 0, doneThisWeek = 0;
        for (Task t : tasks) {
            boolean done = t.getStatus() == TaskStatus.DONE;
            if (!done) {
                open++;
                if (t.getDueDate() != null) {
                    if (t.getDueDate().isBefore(today)) overdue++;
                    else if (t.getDueDate().isEqual(today)) dueToday++;
                }
            } else if (t.getUpdatedAt() != null) {
                LocalDate updated = t.getUpdatedAt().toLocalDate();
                if (!updated.isBefore(weekStart)) doneThisWeek++;
            }
        }

        // sum minutes logged by this employee
        long minutes = 0;
        for (Long tid : taskIds) {
            minutes += taskTimeLogRepository.findByTaskIdOrderByLogDateDesc(tid).stream()
                    .filter(l -> l.getEmployeeId().equals(userId))
                    .mapToLong(TaskTimeLog::getMinutes)
                    .sum();
        }
        long hours = minutes / 60;

        return new MyWorkKpi(open, dueToday, overdue, doneThisWeek, hours);
    }

    // ── helpers ──

    private TaskSummaryResponse toSummary(Task t) {
        List<Long> assigneeIds = taskAssignmentRepository.findByTaskId(t.getId())
                .stream().map(TaskAssignment::getEmployeeId).collect(Collectors.toList());

        String projectName = t.getProjectId() != null
                ? projectRepository.findById(t.getProjectId()).map(Project::getName).orElse(null)
                : null;

        TaskSummaryResponse s = taskMapper.toSummary(t, projectName, null, assigneeIds, null);
        s.setSubtaskCount(taskRepository.countSubtasks(t.getId()));
        s.setSubtaskDone(taskRepository.countSubtasksByStatus(t.getId(), TaskStatus.DONE));
        s.setOpenBlockerCount(taskRepository.countOpenBlockers(t.getId(), TaskStatus.DONE));
        return s;
    }
}