package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreateMilestoneRequest;
import com.mentis.hrms.pm.dto.response.MilestoneResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.entity.Milestone;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.enums.MilestoneStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.pm.service.MilestoneService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MilestoneServiceImpl implements MilestoneService {

    private final MilestoneRepository repo;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;

    @Override
    @Transactional
    public MilestoneResponse create(Long projectId, CreateMilestoneRequest req, Long userId) {
        if (!projectRepository.existsById(projectId))
            throw new ResourceNotFoundException("Project not found: " + projectId);
        Milestone m = Milestone.builder()
                .projectId(projectId).name(req.getName())
                .description(req.getDescription()).dueDate(req.getDueDate())
                .ownerId(req.getOwnerId())
                .status(MilestoneStatus.OPEN)
                .createdBy(userId).build();
        Milestone saved = repo.save(m);
        publisher.publish("POST", "/api/pm/milestones/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    @Override @Transactional(readOnly = true)
    public MilestoneResponse get(Long id) {
        return toResponse(repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + id)));
    }

    @Override @Transactional(readOnly = true)
    public List<MilestoneResponse> listByProject(Long projectId) {
        return repo.findByProjectIdOrderByDueDateAsc(projectId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MilestoneResponse update(Long id, CreateMilestoneRequest req, Long userId) {
        Milestone m = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + id));
        if (req.getName() != null) m.setName(req.getName());
        if (req.getDescription() != null) m.setDescription(req.getDescription());
        if (req.getDueDate() != null) m.setDueDate(req.getDueDate());
        if (req.getOwnerId() != null) m.setOwnerId(req.getOwnerId());
        Milestone saved = repo.save(m);
        publisher.publish("PATCH", "/api/pm/milestones/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public MilestoneResponse complete(Long id, Long userId) {
        Milestone m = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + id));
        m.setStatus(MilestoneStatus.COMPLETED);
        Milestone saved = repo.save(m);
        publisher.publish("POST", "/api/pm/milestones/" + id + "/complete", String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Milestone m = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + id));
        for (Task t : taskRepository.findByMilestoneId(id)) {
            t.setMilestoneId(null);
            taskRepository.save(t);
        }
        repo.delete(m);
        publisher.publish("DELETE", "/api/pm/milestones/" + id, String.valueOf(userId));
    }

    @Override @Transactional(readOnly = true)
    public List<TaskSummaryResponse> milestoneTasks(Long milestoneId) {
        return taskRepository.findByMilestoneId(milestoneId).stream()
                .map(this::toTaskSummary).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addTaskToMilestone(Long milestoneId, Long taskId, Long userId) {
        Milestone m = repo.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
        Task t = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        if (!java.util.Objects.equals(t.getProjectId(), m.getProjectId()))
            throw new com.mentis.hrms.pm.exception.BusinessRuleException("Task belongs to a different project");
        t.setMilestoneId(milestoneId);
        taskRepository.save(t);
        publisher.publish("PATCH", "/api/pm/tasks/" + taskId, String.valueOf(userId));
    }

    @Override
    @Transactional
    public void removeTaskFromMilestone(Long milestoneId, Long taskId, Long userId) {
        Task t = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        t.setMilestoneId(null);
        taskRepository.save(t);
        publisher.publish("PATCH", "/api/pm/tasks/" + taskId, String.valueOf(userId));
    }

    private MilestoneResponse toResponse(Milestone m) {
        List<Task> tasks = taskRepository.findByMilestoneId(m.getId());
        long done = tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        int progress = tasks.isEmpty() ? 0 : (int) Math.round((double) done * 100 / tasks.size());

        // FIX 4: null-safe project name lookup
        String projectName = null;
        try {
            projectName = projectRepository.findById(m.getProjectId())
                    .map(Project::getName).orElse(null);
        } catch (Exception ex) {
            log.warn("milestone {} project {} lookup failed: {}", m.getId(), m.getProjectId(), ex.getMessage());
        }

        // FIX 4: null-safe owner name lookup
        String ownerName = resolveEmployeeName(m.getOwnerId());

        // auto status: overdue → AT_RISK
        if (m.getStatus() == MilestoneStatus.OPEN
                && m.getDueDate() != null
                && m.getDueDate().isBefore(java.time.LocalDate.now()))
            m.setStatus(MilestoneStatus.AT_RISK);

        return MilestoneResponse.builder()
                .id(m.getId()).projectId(m.getProjectId()).projectName(projectName)
                .name(m.getName()).description(m.getDescription())
                .dueDate(m.getDueDate()).status(m.getStatus())
                .ownerId(m.getOwnerId()).ownerName(ownerName)
                .progressPct(progress)
                .taskCount(tasks.size()).doneCount(done)
                .createdAt(m.getCreatedAt()).updatedAt(m.getUpdatedAt())
                .build();
    }

    private TaskSummaryResponse toTaskSummary(Task t) {
        List<Long> assignees = taskAssignmentRepository.findByTaskId(t.getId())
                .stream().map(TaskAssignment::getEmployeeId).toList();
        return TaskSummaryResponse.builder()
                .id(t.getId()).title(t.getTitle())
                .status(t.getStatus()).priority(t.getPriority())
                .dueDate(t.getDueDate()).progressPct(t.getProgressPct())
                .projectId(t.getProjectId()).parentTaskId(t.getParentTaskId())
                .assigneeIds(assignees).build();
    }

    /**
     * FIX 4: null-safe employee name resolution.
     */
    private String resolveEmployeeName(Long id) {
        if (id == null) return null;
        try {
            return employeeRepository.findById(id)
                    .map(e -> {
                        try {
                            String first = e.getFirstName() == null ? "" : e.getFirstName();
                            String last  = e.getLastName()  == null ? "" : e.getLastName();
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

    private String safe(String s) { return s == null ? "" : s; }
}