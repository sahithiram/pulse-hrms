package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.CreateIssueRequest;
import com.mentis.hrms.pm.dto.response.IssueResponse;
import com.mentis.hrms.pm.entity.Issue;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.realtime.PmEventPublisher;
import com.mentis.hrms.pm.repository.IssueRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.service.IssueService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueServiceImpl implements IssueService {

    private final IssueRepository repo;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final PmEventPublisher publisher;
    private final NotificationEmitter notificationEmitter;

    @Override
    @Transactional
    public IssueResponse create(CreateIssueRequest req, Long userId) {
        if (!projectRepository.existsById(req.getProjectId()))
            throw new ResourceNotFoundException("Project not found: " + req.getProjectId());

        Issue i = Issue.builder()
                .projectId(req.getProjectId())
                .linkedTaskId(req.getLinkedTaskId())
                .title(req.getTitle())
                .description(req.getDescription())
                .severity(req.getSeverity() != null ? req.getSeverity() : "MEDIUM")
                .priority(req.getPriority() != null ? req.getPriority() : "MEDIUM")
                .type(req.getType() != null ? req.getType() : "BUG")
                .status("OPEN")
                .assigneeId(req.getAssigneeId())
                .reportedBy(userId)
                .build();
        Issue saved = repo.save(i);

        // Notify the assignee + project owner
        String title = saved.getTitle();
        if (saved.getAssigneeId() != null && !saved.getAssigneeId().equals(userId)) {
            notifyEmployee(saved.getAssigneeId(), "ISSUE_ASSIGNED",
                    "New issue assigned: " + title,
                    "You were assigned an issue in project " + saved.getProjectId(),
                    saved.getProjectId(), null, userId, "HIGH",
                    "/pm/09-quality?tab=issues&id=" + saved.getId());
        }
        projectRepository.findById(saved.getProjectId()).ifPresent(p -> {
            if (p.getOwnerId() != null && !p.getOwnerId().equals(userId)
                    && !p.getOwnerId().equals(saved.getAssigneeId())) {
                notifyEmployee(p.getOwnerId(), "ISSUE_CREATED",
                        "New issue: " + title,
                        "Severity " + saved.getSeverity() + " reported",
                        saved.getProjectId(), null, userId,
                        "CRITICAL".equalsIgnoreCase(saved.getSeverity()) ? "HIGH" : "NORMAL",
                        "/pm/09-quality?tab=issues&id=" + saved.getId());
            }
        });

        publisher.publish("POST", "/api/pm/issues/" + saved.getId(), String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public IssueResponse get(Long id) {
        return toResponse(repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listByProject(Long projectId) {
        return repo.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IssueResponse> listByTask(Long taskId) {
        return repo.findByLinkedTaskIdOrderByCreatedAtDesc(taskId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public IssueResponse updateStatus(Long id, String status, Long userId) {
        Issue i = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + id));
        String old = i.getStatus();
        i.setStatus(status);
        if ("RESOLVED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)) {
            if (i.getResolvedAt() == null) i.setResolvedAt(LocalDateTime.now());
        }
        Issue saved = repo.save(i);

        if (i.getReportedBy() != null && !i.getReportedBy().equals(userId)) {
            notifyEmployee(i.getReportedBy(), "ISSUE_STATUS_CHANGED",
                    "Issue status changed: " + saved.getTitle(),
                    old + " → " + status,
                    saved.getProjectId(), null, userId, "NORMAL",
                    "/pm/09-quality?tab=issues&id=" + saved.getId());
        }

        publisher.publish("PATCH", "/api/pm/issues/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public IssueResponse assign(Long id, Long assigneeId, Long userId) {
        Issue i = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + id));
        i.setAssigneeId(assigneeId);
        Issue saved = repo.save(i);

        if (assigneeId != null && !assigneeId.equals(userId)) {
            notifyEmployee(assigneeId, "ISSUE_ASSIGNED",
                    "New issue assigned: " + saved.getTitle(),
                    "You were assigned an issue",
                    saved.getProjectId(), null, userId, "HIGH",
                    "/pm/09-quality?tab=issues&id=" + saved.getId());
        }

        publisher.publish("PATCH", "/api/pm/issues/" + id, String.valueOf(userId));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Issue i = repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Issue not found: " + id));
        repo.delete(i);
        publisher.publish("DELETE", "/api/pm/issues/" + id, String.valueOf(userId));
    }

    // ── helpers ──

    private IssueResponse toResponse(Issue i) {
        String projectName = null;
        if (i.getProjectId() != null) {
            try {
                projectName = projectRepository.findById(i.getProjectId())
                        .map(Project::getName).orElse(null);
            } catch (Exception ex) {
                log.warn("issue project {} lookup failed: {}", i.getProjectId(), ex.getMessage());
            }
        }
        String linkedTaskTitle = null;
        if (i.getLinkedTaskId() != null) {
            try {
                linkedTaskTitle = taskRepository.findById(i.getLinkedTaskId())
                        .map(Task::getTitle).orElse(null);
            } catch (Exception ex) {
                log.warn("issue linked task {} lookup failed: {}", i.getLinkedTaskId(), ex.getMessage());
            }
        }
        return IssueResponse.builder()
                .id(i.getId())
                .projectId(i.getProjectId()).projectName(projectName)
                .linkedTaskId(i.getLinkedTaskId()).linkedTaskTitle(linkedTaskTitle)
                .title(i.getTitle()).description(i.getDescription())
                .severity(i.getSeverity()).priority(i.getPriority())
                .status(i.getStatus()).type(i.getType())
                .assigneeId(i.getAssigneeId()).assigneeName(nameOf(i.getAssigneeId()))
                .reportedBy(i.getReportedBy()).reportedByName(nameOf(i.getReportedBy()))
                .resolvedAt(i.getResolvedAt())
                .createdAt(i.getCreatedAt()).updatedAt(i.getUpdatedAt())
                .build();
    }

    /**
     * FIX 4: null-safe employee name resolution.
     */
    private String nameOf(Long pk) {
        if (pk == null) return null;
        try {
            return employeeRepository.findById(pk)
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
                        return "Employee #" + pk;
                    })
                    .orElse("Employee #" + pk);
        } catch (Exception ex) {
            log.warn("nameOf failed for pk={}: {}", pk, ex.getMessage());
            return "Employee #" + pk;
        }
    }

    private String safe(String s) { return s == null ? "" : s; }

    /**
     * FIX 8: null-safe recipient code resolution + defensive notify.
     */
    private void notifyEmployee(Long empPk, String type, String title, String message,
                                Long projectId, Long taskId, Long actorPk, String priority,
                                String deepLink) {
        if (empPk == null) return;
        try {
            String code;
            try {
                code = employeeRepository.findById(empPk).map(Employee::getEmployeeId).orElse(null);
            } catch (Exception ex) {
                log.warn("notify: employee {} lookup failed: {}", empPk, ex.getMessage());
                code = null;
            }
            if (code == null) return;
            String actorName = nameOf(actorPk);
            notificationEmitter.emit(NotificationRequest.builder()
                    .recipientId(code).recipientType("EMPLOYEE")
                    .type(type).action(type)
                    .title(title).message(message)
                    .projectId(projectId).taskId(taskId)
                    .actorId(actorPk).actorName(actorName)
                    .priority(priority)
                    .deepLink(deepLink)
                    .build());
        } catch (Exception e) {
            log.warn("issue notify failed: {}", e.getMessage());
        }
    }
}