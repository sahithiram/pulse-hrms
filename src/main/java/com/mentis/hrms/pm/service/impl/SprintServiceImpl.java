package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.CompleteSprintRequest;
import com.mentis.hrms.pm.dto.request.CreateSprintRequest;
import com.mentis.hrms.pm.dto.response.BurndownResponse;
import com.mentis.hrms.pm.dto.response.SprintResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.dto.response.VelocityResponse;
import com.mentis.hrms.pm.entity.*;
import com.mentis.hrms.pm.enums.SprintStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.SprintService;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SprintServiceImpl implements SprintService {

    private final SprintRepository sprintRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ActivityLogRepository activityLogRepository;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;
    private final NotificationEmitter notificationEmitter;

    // ═══════════════════════════ CRUD ═══════════════════════════

    @Override
    @Transactional
    public SprintResponse create(Long projectId, CreateSprintRequest req, Long userId) {
        if (!projectRepository.existsById(projectId))
            throw new ResourceNotFoundException("Project not found: " + projectId);
        if (req.getEndDate().isBefore(req.getStartDate()))
            throw new BusinessRuleException("End date cannot be before start date");

        assertNoOverlap(projectId, null, req.getStartDate(), req.getEndDate());

        Sprint s = Sprint.builder()
                .projectId(projectId)
                .name(req.getName())
                .goal(req.getGoal())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .velocity(req.getVelocity())
                .status(SprintStatus.PLANNED)
                .createdBy(userId)
                .build();
        Sprint saved = sprintRepository.save(s);
        publisher.publish("POST", "/api/pm/sprints/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SprintResponse get(Long id) {
        return toResponse(sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SprintResponse> listByProject(Long projectId) {
        return sprintRepository.findByProjectIdOrderByStartDateDesc(projectId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SprintResponse update(Long id, CreateSprintRequest req, Long userId) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
        if (s.getStatus() == SprintStatus.COMPLETED)
            throw new BusinessRuleException("A completed sprint can't be edited");

        LocalDate start = req.getStartDate() != null ? req.getStartDate() : s.getStartDate();
        LocalDate end = req.getEndDate() != null ? req.getEndDate() : s.getEndDate();
        if (start != null && end != null && end.isBefore(start))
            throw new BusinessRuleException("End date cannot be before start date");

        if (req.getStartDate() != null || req.getEndDate() != null)
            assertNoOverlap(s.getProjectId(), id, start, end);

        if (req.getName() != null) s.setName(req.getName());
        if (req.getGoal() != null) s.setGoal(req.getGoal());
        if (req.getStartDate() != null) s.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) s.setEndDate(req.getEndDate());
        if (req.getVelocity() != null) s.setVelocity(req.getVelocity());
        Sprint saved = sprintRepository.save(s);
        publisher.publish("PATCH", "/api/pm/sprints/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    private void assertNoOverlap(Long projectId, Long selfId, LocalDate start, LocalDate end) {
        if (start == null || end == null) return;
        for (Sprint o : sprintRepository.findByProjectIdOrderByStartDateDesc(projectId)) {
            if (selfId != null && selfId.equals(o.getId())) continue;
            if (o.getStartDate() == null || o.getEndDate() == null) continue;
            boolean overlaps = !start.isAfter(o.getEndDate()) && !end.isBefore(o.getStartDate());
            if (overlaps)
                throw new BusinessRuleException("Dates overlap with sprint \"" + o.getName() + "\"");
        }
    }

    // ═══════════════════════════ START ═══════════════════════════

    @Override
    @Transactional
    public SprintResponse start(Long id, Long userId) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
        if (s.getStatus() == SprintStatus.ACTIVE)
            throw new BusinessRuleException("Sprint is already active");
        if (s.getStatus() == SprintStatus.COMPLETED)
            throw new BusinessRuleException("Completed sprint cannot be restarted");

        List<Sprint> active = sprintRepository.findByProjectIdAndStatusOrderByStartDateDesc(
                s.getProjectId(), SprintStatus.ACTIVE);
        if (!active.isEmpty())
            throw new BusinessRuleException("Another sprint is already active for this project. Complete it first.");

        s.setStatus(SprintStatus.ACTIVE);
        Sprint saved = sprintRepository.save(s);

        List<Task> tasks = taskRepository.findBySprintId(id);
        Set<Long> audience = new HashSet<>();
        for (Task t : tasks)
            taskAssignmentRepository.findByTaskId(t.getId()).forEach(a -> audience.add(a.getEmployeeId()));
        for (Long pk : audience) {
            String code;
            try {
                code = employeeRepository.findById(pk).map(Employee::getEmployeeId).orElse(null);
            } catch (Exception ex) {
                log.warn("sprint start: employee {} lookup failed: {}", pk, ex.getMessage());
                code = null;
            }
            if (code == null) continue;
            try {
                notificationEmitter.emit(NotificationRequest.builder()
                        .recipientId(code).recipientType("EMPLOYEE")
                        .type("SPRINT_STARTED").action("SPRINT_STARTED")
                        .title("🚀 Sprint started: " + saved.getName())
                        .message("Goal: " + (saved.getGoal() != null ? saved.getGoal() : "—"))
                        .projectId(saved.getProjectId())
                        .actorId(userId).actorName(resolveEmployeeName(userId))
                        .priority("HIGH")
                        .deepLink("/pm/05-planning?tab=sprints&sprint=" + saved.getId())
                        .build());
            } catch (Exception e) {
                log.warn("sprint start notif failed: {}", e.getMessage());
            }
        }

        publisher.publish("POST", "/api/pm/sprints/" + id + "/start", String.valueOf(userId));
        return toResponse(saved);
    }

    // ═══════════════════════════ COMPLETE (Phase 4) ═══════════════════════════

    @Override
    @Transactional
    public SprintResponse complete(Long id, CompleteSprintRequest req, Long userId) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
        if (s.getStatus() != SprintStatus.ACTIVE)
            throw new BusinessRuleException("Only an active sprint can be completed");

        // where do the unfinished tasks go?
        Sprint target = null;
        Long carryId = req == null ? null : req.getCarryOverSprintId();
        if (carryId != null) {
            target = sprintRepository.findById(carryId)
                    .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + carryId));
            if (target.getId().equals(s.getId())
                    || !Objects.equals(target.getProjectId(), s.getProjectId())
                    || target.getStatus() != SprintStatus.PLANNED)
                throw new BusinessRuleException(
                        "Carry-over sprint must be a planned (not started) sprint of the same project");
        }

        List<Task> tasks = taskRepository.findBySprintId(id);
        long committed = 0, completed = 0;
        int carried = 0, returned = 0;
        for (Task t : tasks) {
            int pts = t.getStoryPoints() == null ? 0 : t.getStoryPoints();
            committed += pts;
            if (t.getStatus() == TaskStatus.DONE) {
                completed += pts;
                continue;
            }
            if (target != null) { t.setSprintId(target.getId()); carried++; }
            else { t.setSprintId(null); returned++; }
            taskRepository.save(t);
        }

        s.setStatus(SprintStatus.COMPLETED);
        s.setCommittedPoints((int) committed);      // snapshot: tasks that moved out no longer count
        s.setVelocity((int) completed);             // real velocity = points actually finished
        if (req != null && req.getRetroNotes() != null && !req.getRetroNotes().isBlank())
            s.setRetroNotes(req.getRetroNotes().trim());
        Sprint saved = sprintRepository.save(s);

        publisher.publish("POST", "/api/pm/sprints/" + id + "/complete", String.valueOf(userId));
        log.info("Sprint {} completed — velocity {} pts, {} task(s) carried over, {} returned to backlog",
                id, completed, carried, returned);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public SprintResponse updateRetro(Long id, String retroNotes, Long userId) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
        s.setRetroNotes(retroNotes == null || retroNotes.isBlank() ? null : retroNotes.trim());
        Sprint saved = sprintRepository.save(s);
        publisher.publish("PATCH", "/api/pm/sprints/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public VelocityResponse velocity(Long projectId) {
        List<Integer> recent = sprintRepository
                .findByProjectIdAndStatusOrderByStartDateDesc(projectId, SprintStatus.COMPLETED).stream()
                .map(Sprint::getVelocity)
                .filter(Objects::nonNull)
                .limit(3)
                .collect(Collectors.toList());
        double avg = recent.isEmpty() ? 0 : recent.stream().mapToInt(Integer::intValue).average().orElse(0);
        return VelocityResponse.builder()
                .sprintsCounted(recent.size())
                .averageVelocity(Math.round(avg * 10.0) / 10.0)
                .suggestedCommitment((int) Math.round(avg))
                .recentVelocities(recent)
                .build();
    }

    // ═══════════════════════════ DELETE / TASKS ═══════════════════════════

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + id));
        if (s.getStatus() == SprintStatus.ACTIVE)
            throw new BusinessRuleException("Cannot delete an active sprint. Complete it first.");

        for (Task t : taskRepository.findBySprintId(id)) {
            t.setSprintId(null);
            taskRepository.save(t);
        }
        sprintRepository.deleteById(id);
        publisher.publish("DELETE", "/api/pm/sprints/" + id, String.valueOf(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> sprintTasks(Long sprintId) {
        if (!sprintRepository.existsById(sprintId))
            throw new ResourceNotFoundException("Sprint not found: " + sprintId);
        return taskRepository.findBySprintId(sprintId).stream()
                .map(this::toTaskSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addTaskToSprint(Long sprintId, Long taskId, Long userId) {
        Sprint s = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        if (s.getStatus() == SprintStatus.COMPLETED)
            throw new BusinessRuleException("Cannot add tasks to a completed sprint");
        Task t = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (!Objects.equals(t.getProjectId(), s.getProjectId()))
            throw new BusinessRuleException("Task belongs to a different project");
        t.setSprintId(sprintId);
        taskRepository.save(t);
        publisher.publish("PATCH", "/api/pm/tasks/" + taskId, String.valueOf(userId));
    }

    @Override
    @Transactional
    public void removeTaskFromSprint(Long sprintId, Long taskId, Long userId) {
        Sprint s = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));
        if (s.getStatus() == SprintStatus.COMPLETED)
            throw new BusinessRuleException("Cannot remove tasks from a completed sprint");
        Task t = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        t.setSprintId(null);
        taskRepository.save(t);
        publisher.publish("PATCH", "/api/pm/tasks/" + taskId, String.valueOf(userId));
    }

    // ═══════════════════════════ BURNDOWN ═══════════════════════════

    @Override
    @Transactional(readOnly = true)
    public BurndownResponse burndown(Long sprintId) {
        Sprint s = sprintRepository.findById(sprintId)
                .orElseThrow(() -> new ResourceNotFoundException("Sprint not found: " + sprintId));

        List<Task> tasks = taskRepository.findBySprintId(sprintId);
        if (tasks.isEmpty()) {
            return BurndownResponse.builder()
                    .sprintId(sprintId).sprintName(s.getName())
                    .startDate(s.getStartDate()).endDate(s.getEndDate())
                    .totalPoints(0).points(List.of()).ideal(List.of()).build();
        }

        List<Long> taskIds = tasks.stream().map(Task::getId).toList();
        long totalPoints = tasks.stream()
                .mapToLong(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();

        LocalDate start = s.getStartDate() != null ? s.getStartDate() : LocalDate.now().minusDays(7);
        LocalDate end = s.getEndDate() != null ? s.getEndDate() : LocalDate.now();
        LocalDate today = LocalDate.now();
        LocalDate lastDay = today.isBefore(end) ? today : end;

        List<ActivityLog> doneEvents = activityLogRepository.findDoneTransitions(
                taskIds, start.atStartOfDay());

        // taskId → date of its LAST "became DONE" event (events come oldest → newest)
        Map<Long, LocalDate> doneBy = new HashMap<>();
        for (ActivityLog e : doneEvents) {
            if (e.getCreatedAt() == null) continue;
            doneBy.put(e.getEntityId(), e.getCreatedAt().toLocalDate());
        }

        List<BurndownResponse.Point> points = new ArrayList<>();
        List<BurndownResponse.Point> ideal = new ArrayList<>();
        long totalDays = ChronoUnit.DAYS.between(start, end);
        if (totalDays <= 0) totalDays = 1;
        for (LocalDate d = start; !d.isAfter(lastDay); d = d.plusDays(1)) {
            long donePoints = 0;
            for (Task t : tasks) {
                // a task that was reopened (not DONE now) never counts as finished
                if (t.getStatus() != TaskStatus.DONE) continue;
                LocalDate donAt = doneBy.get(t.getId());
                if (donAt != null && !donAt.isAfter(d))
                    donePoints += t.getStoryPoints() != null ? t.getStoryPoints() : 0;
            }
            long remaining = totalPoints - donePoints;
            points.add(BurndownResponse.Point.builder()
                    .date(d).remainingPoints(remaining).completedPoints(donePoints).build());
        }
        for (long i = 0; i <= totalDays; i++) {
            LocalDate d = start.plusDays(i);
            long idealRemaining = Math.round(totalPoints * (1.0 - (double) i / totalDays));
            ideal.add(BurndownResponse.Point.builder()
                    .date(d).idealPoints(idealRemaining).build());
        }

        return BurndownResponse.builder()
                .sprintId(sprintId).sprintName(s.getName())
                .startDate(start).endDate(end).totalPoints(totalPoints)
                .points(points).ideal(ideal).build();
    }

    // ═══════════════════════════ helpers ═══════════════════════════

    private SprintResponse toResponse(Sprint s) {
        List<Task> tasks = taskRepository.findBySprintId(s.getId());
        long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        long committed = tasks.stream().mapToLong(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
        // a closed sprint reports what was committed when it closed, not what is left in it
        if (s.getStatus() == SprintStatus.COMPLETED && s.getCommittedPoints() != null)
            committed = s.getCommittedPoints();
        long completed = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .mapToLong(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0).sum();
        long daysLeft = s.getEndDate() != null
                ? Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), s.getEndDate())) : 0;

        String projectName = null;
        try {
            projectName = projectRepository.findById(s.getProjectId())
                    .map(Project::getName).orElse(null);
        } catch (Exception ex) {
            log.warn("sprint {} project {} lookup failed: {}", s.getId(), s.getProjectId(), ex.getMessage());
        }

        return SprintResponse.builder()
                .id(s.getId()).projectId(s.getProjectId()).projectName(projectName)
                .name(s.getName()).goal(s.getGoal())
                .startDate(s.getStartDate()).endDate(s.getEndDate())
                .status(s.getStatus()).velocity(s.getVelocity())
                .retroNotes(s.getRetroNotes())
                .taskCount(tasks.size()).doneCount(done)
                .committedPoints(committed).completedPoints(completed)
                .daysRemaining(daysLeft)
                .createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> backlog(Long projectId) {
        return taskRepository.findBacklog(projectId)
                .stream().map(this::toTaskSummary).collect(Collectors.toList());
    }

    private TaskSummaryResponse toTaskSummary(Task t) {
        List<Long> assignees = taskAssignmentRepository.findByTaskId(t.getId())
                .stream().map(TaskAssignment::getEmployeeId).toList();

        String projectName = null;
        try {
            projectName = projectRepository.findById(t.getProjectId())
                    .map(Project::getName).orElse(null);
        } catch (Exception ex) {
            log.warn("task {} project {} lookup failed: {}", t.getId(), t.getProjectId(), ex.getMessage());
        }

        return TaskSummaryResponse.builder()
                .id(t.getId()).title(t.getTitle())
                .status(t.getStatus()).priority(t.getPriority())
                .dueDate(t.getDueDate()).progressPct(t.getProgressPct())
                .storyPoints(t.getStoryPoints())
                .projectId(t.getProjectId())
                .projectName(projectName)
                .teamId(t.getTeamId())
                .parentTaskId(t.getParentTaskId())
                .assigneeIds(assignees)
                .build();
    }

    private String resolveEmployeeName(Long id) {
        if (id == null) return null;
        try {
            return employeeRepository.findById(id)
                    .map(e -> {
                        try {
                            String first = e.getFirstName() == null ? "" : e.getFirstName();
                            String last = e.getLastName() == null ? "" : e.getLastName();
                            String full = (first + " " + last).trim();
                            if (!full.isEmpty()) return full;
                            if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank())
                                return e.getEmployeeName();
                            if (e.getEmail() != null) return e.getEmail();
                        } catch (Exception ignored) {
                        }
                        return "Employee #" + id;
                    })
                    .orElse("Employee #" + id);
        } catch (Exception ex) {
            log.warn("resolveEmployeeName failed for id={}: {}", id, ex.getMessage());
            return "Employee #" + id;
        }
    }
}