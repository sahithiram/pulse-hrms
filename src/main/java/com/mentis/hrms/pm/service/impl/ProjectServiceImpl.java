package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.entity.ActivityLog;
import com.mentis.hrms.pm.entity.Client;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.mapper.ProjectMapper;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ClientRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.service.ProjectService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.service.ProjectClosureService;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.HealthMode;
import java.time.LocalDateTime;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final ClientRepository clientRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogRepository activityLogRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectClosureService closureService;

    /** Allowed project status moves. Anything not listed is rejected. */
    private static final Map<ProjectStatus, Set<ProjectStatus>> FLOW = new EnumMap<>(ProjectStatus.class);
    static {
        FLOW.put(ProjectStatus.PLANNING,  EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ON_HOLD, ProjectStatus.ARCHIVED));
        FLOW.put(ProjectStatus.ACTIVE,    EnumSet.of(ProjectStatus.ON_HOLD, ProjectStatus.AT_RISK, ProjectStatus.DELAYED, ProjectStatus.COMPLETED));
        FLOW.put(ProjectStatus.ON_HOLD,   EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED));
        FLOW.put(ProjectStatus.AT_RISK,   EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.DELAYED, ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED));
        FLOW.put(ProjectStatus.DELAYED,   EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.AT_RISK, ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED));
        FLOW.put(ProjectStatus.COMPLETED, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED));
        FLOW.put(ProjectStatus.ARCHIVED,  EnumSet.of(ProjectStatus.PLANNING));
    }

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest req, Long currentUserId) {
        validateDates(req.getStartDate(), req.getEndDate());
        validateClient(req.getClientId());

        String code = req.getCode();
        if (code == null || code.isBlank()) {
            code = "PRJ-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }
        if (projectRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Project code already exists: " + code);
        }
        Project project = projectMapper.toEntity(req, currentUserId);
        project.setCode(code);
        Project saved = projectRepository.save(project);
        addInitialMembers(saved, currentUserId, req.getMemberIds());
        logActivity(saved.getId(), "CREATED", currentUserId, null, saved.getStatus().name(),
                "created project " + saved.getName());
        log.info("Project created id={} code={}", saved.getId(), saved.getCode());
        return projectMapper.toResponse(saved, resolveClientName(saved.getClientId()));
    }

    @Override
    @Transactional
    public ProjectResponse updateProject(Long id, UpdateProjectRequest req, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));

        LocalDate newStart = req.getStartDate() != null ? req.getStartDate() : project.getStartDate();
        LocalDate newEnd = req.getEndDate() != null ? req.getEndDate() : project.getEndDate();
        validateDates(newStart, newEnd);
        if (req.getClientId() != null) validateClient(req.getClientId());
        if (req.getLessonsLearned() != null) project.setLessonsLearned(req.getLessonsLearned().trim());
        ProjectStatus oldStatus = project.getStatus();
        if (req.getStatus() != null && req.getStatus() != oldStatus) {
            checkTransition(project, oldStatus, req.getStatus());
        }

        if (req.getName() != null) project.setName(req.getName());
        if (req.getDescription() != null) project.setDescription(req.getDescription());
        if (req.getOwnerId() != null) project.setOwnerId(req.getOwnerId());
        if (req.getDepartmentId() != null) project.setDepartmentId(req.getDepartmentId());
        if (req.getClientId() != null) project.setClientId(req.getClientId());
        if (req.getTeamId() != null) project.setTeamId(req.getTeamId() == 0L ? null : req.getTeamId());
        if (req.getStatus() != null) project.setStatus(req.getStatus());
        if (oldStatus == ProjectStatus.PLANNING && project.getStatus() == ProjectStatus.ACTIVE
                && project.getBaselinedAt() == null
                && project.getStartDate() != null && project.getEndDate() != null) {
            applyBaseline(project, currentUserId);
        }
        if (req.getHealth() != null) {
            if (req.getHealth() != project.getHealth()) project.setHealthMode(HealthMode.MANUAL);
            project.setHealth(req.getHealth());
        }
        if (req.getPriority() != null) project.setPriority(req.getPriority());
        if (req.getStartDate() != null) project.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) project.setEndDate(req.getEndDate());
        if (req.getBudgetTotal() != null) project.setBudgetTotal(req.getBudgetTotal());
        if (req.getProgressPct() != null) project.setProgressPct(req.getProgressPct());
        if (project.getStatus() == ProjectStatus.COMPLETED) project.setProgressPct(100);

        Project saved = projectRepository.save(project);
        if (req.getOwnerId() != null) ensureManager(saved.getId(), saved.getOwnerId());
        if (oldStatus != saved.getStatus()) {
            logActivity(id, "STATUS_CHANGED", currentUserId, oldStatus.name(), saved.getStatus().name(),
                    "changed status of " + saved.getName() + " from " + oldStatus + " to " + saved.getStatus());
        } else {
            logActivity(id, "UPDATED", currentUserId, null, null, "updated project " + saved.getName());
        }
        return projectMapper.toResponse(saved, resolveClientName(saved.getClientId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        return projectMapper.toResponse(project, resolveClientName(project.getClientId()));
    }

    @Override

    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> listProjects(String search, ProjectStatus status,
                                                             Long ownerId, Pageable pageable) {
        return toSummaryPage(projectRepository.search(search, status, ownerId, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryResponse> listVisibleProjects(String search, ProjectStatus status,
                                                                    Long ownerId, Long viewerId,
                                                                    Pageable pageable) {
        return toSummaryPage(projectRepository.searchVisible(search, status, ownerId, viewerId, pageable));
    }

    private PageResponse<ProjectSummaryResponse> toSummaryPage(Page<Project> page) {
        List<Long> clientIds = page.getContent().stream()
                .map(Project::getClientId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> clientNameMap = clientRepository.findAllById(clientIds).stream()
                .collect(Collectors.toMap(
                        Client::getId,
                        c -> c.getName() == null ? "" : c.getName(),
                        (existing, replacement) -> existing));
        Page<ProjectSummaryResponse> mapped = page.map(p ->
                projectMapper.toSummary(p, clientNameMap.get(p.getClientId())));
        return PageResponse.of(mapped);
    }

    @Override
    @Transactional
    public ProjectResponse archiveProject(Long id, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        ProjectStatus old = project.getStatus();
        if (old != ProjectStatus.ARCHIVED) checkTransition(project, old, ProjectStatus.ARCHIVED);
        project.setStatus(ProjectStatus.ARCHIVED);
        Project saved = projectRepository.save(project);
        logActivity(id, "STATUS_CHANGED", currentUserId, old.name(), "ARCHIVED", "archived project " + saved.getName());
        return projectMapper.toResponse(saved, resolveClientName(saved.getClientId()));
    }

    @Override
    @Transactional
    public void deleteProject(Long id, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        long tasks = countTasks(id, TaskStatus.values());
        if (tasks > 0) {
            throw new IllegalArgumentException("Cannot delete a project that has " + tasks
                    + " task(s). Archive it instead.");
        }
        // FIX 11 — log BEFORE delete so the activity row references a live project
        logActivity(id, "DELETED", currentUserId, null, null, "deleted project " + project.getName());
        closureService.deleteForProject(id);
        projectMemberRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
        log.info("Project deleted id={}", id);
    }

    // ---------------------------------------------------------------- rules

    private void checkTransition(Project project, ProjectStatus from, ProjectStatus to) {
        if (!FLOW.getOrDefault(from, EnumSet.noneOf(ProjectStatus.class)).contains(to)) {
            throw new IllegalArgumentException("A project cannot move from " + from + " to " + to + ".");
        }
        if (to == ProjectStatus.COMPLETED) {
            List<String> blockers = closureService.blockers(project);
            if (!blockers.isEmpty()) {
                throw new IllegalArgumentException("Cannot complete the project: " + String.join("; ", blockers) + ".");
            }
        }
    }
    /** Owner + creator become MANAGER; people picked in the create form become MEMBER. */
    private void addInitialMembers(Project saved, Long creatorPk, List<Long> memberIds) {
        ensureManager(saved.getId(), saved.getOwnerId());
        if (creatorPk != null && !creatorPk.equals(saved.getOwnerId())) ensureManager(saved.getId(), creatorPk);
        if (memberIds == null) return;
        for (Long empId : memberIds) {
            if (empId == null) continue;
            if (projectMemberRepository.findByProjectIdAndEmployeeId(saved.getId(), empId).isPresent()) continue;
            if (!employeeRepository.existsById(empId)) continue;
            projectMemberRepository.save(ProjectMember.builder()
                    .projectId(saved.getId()).employeeId(empId)
                    .role(ProjectRole.MEMBER).allocationPct(100).build());
        }
    }

    private void ensureManager(Long projectId, Long employeePk) {
        if (projectId == null || employeePk == null) return;
        ProjectMember m = projectMemberRepository.findByProjectIdAndEmployeeId(projectId, employeePk).orElse(null);
        if (m == null) {
            projectMemberRepository.save(ProjectMember.builder()
                    .projectId(projectId).employeeId(employeePk)
                    .role(ProjectRole.MANAGER).allocationPct(100).build());
        } else if (m.getRole() != ProjectRole.MANAGER) {
            m.setRole(ProjectRole.MANAGER);
            projectMemberRepository.save(m);
        }
    }
    private long countTasks(Long projectId, TaskStatus... statuses) {
        long total = 0;
        for (TaskStatus s : statuses) {
            total += taskRepository.countByProjectIdAndStatus(projectId, s);
        }
        return total;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("End date cannot be before the start date.");
        }
    }

    private void validateClient(Long clientId) {
        if (clientId != null && !clientRepository.existsById(clientId)) {
            throw new ResourceNotFoundException("Client not found: " + clientId);
        }
    }

    // -------------------------------------------------------------- helpers
    @Override
    @Transactional
    public ProjectResponse saveBaseline(Long id, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getStartDate() == null || project.getEndDate() == null)
            throw new IllegalArgumentException("Set the start and end dates before saving a baseline.");
        applyBaseline(project, currentUserId);
        Project saved = projectRepository.save(project);
        logActivity(id, "BASELINED", currentUserId, null, null,
                "saved a baseline for " + saved.getName() + " (" + saved.getStartDate() + " → "
                        + saved.getEndDate() + ", budget " + saved.getBudgetTotal() + ")");
        return projectMapper.toResponse(saved, resolveClientName(saved.getClientId()));
    }

    private void applyBaseline(Project p, Long userId) {
        p.setBaselineStart(p.getStartDate());
        p.setBaselineEnd(p.getEndDate());
        p.setBaselineBudget(p.getBudgetTotal());
        p.setBaselinedAt(LocalDateTime.now());
        p.setBaselinedBy(userId);
    }
    private void logActivity(Long projectId, String action, Long actorId, String oldV, String newV, String msg) {
        try {
            String actor = actorId == null ? "System"
                    : employeeRepository.findById(actorId)
                    .map(e -> ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                            + (e.getLastName() == null ? "" : e.getLastName())).trim())
                    .orElse("User #" + actorId);
            activityLogRepository.save(ActivityLog.builder()
                    .entityType("PROJECT").entityId(projectId).action(action)
                    .actorId(actorId).actorName(actor)
                    .oldValue(oldV).newValue(newV).message(msg).build());
        } catch (Exception e) {
            log.warn("Could not write activity log for project {}: {}", projectId, e.getMessage());
        }
    }

    private String resolveClientName(Long clientId) {
        if (clientId == null) return null;
        return clientRepository.findById(clientId).map(Client::getName).orElse(null);
    }
}