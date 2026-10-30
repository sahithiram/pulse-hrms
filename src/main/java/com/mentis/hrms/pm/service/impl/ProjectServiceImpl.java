package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.BaselineHistoryResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.entity.*;
import com.mentis.hrms.pm.enums.HealthMode;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.mapper.ProjectMapper;
import com.mentis.hrms.pm.repository.ActivityLogRepository;
import com.mentis.hrms.pm.repository.ClientRepository;
import com.mentis.hrms.pm.repository.ProjectBaselineHistoryRepository;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.service.ProjectClosureService;
import com.mentis.hrms.pm.service.ProjectService;
import com.mentis.hrms.pm.service.ProjectVarianceService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ProjectVarianceService varianceService;
    private final ProjectBaselineHistoryRepository baselineHistoryRepository;

    /** Allowed project status moves. Anything not listed is rejected. */
    private static final Map<ProjectStatus, Set<ProjectStatus>> FLOW = new EnumMap<>(ProjectStatus.class);
    static {
        FLOW.put(ProjectStatus.PLANNING, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ON_HOLD, ProjectStatus.ARCHIVED));
        FLOW.put(ProjectStatus.ACTIVE, EnumSet.of(ProjectStatus.ON_HOLD, ProjectStatus.AT_RISK, ProjectStatus.DELAYED, ProjectStatus.COMPLETED));
        FLOW.put(ProjectStatus.ON_HOLD, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED));
        FLOW.put(ProjectStatus.AT_RISK, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.DELAYED, ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED));
        FLOW.put(ProjectStatus.DELAYED, EnumSet.of(ProjectStatus.ACTIVE, ProjectStatus.AT_RISK, ProjectStatus.ON_HOLD, ProjectStatus.COMPLETED));
        FLOW.put(ProjectStatus.COMPLETED, EnumSet.of(ProjectStatus.ARCHIVED)); // STEP 8: reopening has its own endpoint
        FLOW.put(ProjectStatus.ARCHIVED, EnumSet.of(ProjectStatus.PLANNING));
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
    @Transactional(readOnly = true)
    public List<BaselineHistoryResponse> baselineHistory(Long id) {
        return baselineHistoryRepository.findByProjectIdOrderByVersionNoDesc(id).stream()
                .map(h -> new BaselineHistoryResponse(h.getVersionNo(), h.getBaselineStart(), h.getBaselineEnd(),
                        h.getBaselineBudget(), h.getBaselinedAt(), h.getBaselinedBy(),
                        h.getSupersededAt(), h.getChangeRequestId(), h.getReason()))
                .collect(Collectors.toList());
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
            // STEP 3: the kick-off gate looks at the dates this same request is about to save
            checkTransition(project, oldStatus, req.getStatus(), newStart, newEnd);
        }

        if (req.getName() != null) project.setName(req.getName());
        if (req.getDescription() != null) project.setDescription(req.getDescription());
        if (req.getOwnerId() != null) project.setOwnerId(req.getOwnerId());
        if (req.getDepartmentId() != null) project.setDepartmentId(req.getDepartmentId());
        if (req.getClientId() != null) project.setClientId(req.getClientId());
        if (req.getTeamId() != null) project.setTeamId(req.getTeamId() == 0L ? null : req.getTeamId());
        if (req.getStatus() != null) project.setStatus(req.getStatus());
        if (oldStatus != ProjectStatus.COMPLETED && project.getStatus() == ProjectStatus.COMPLETED) {
            project.setCompletedAt(LocalDateTime.now()); // STEP 8
            project.setCompletedBy(currentUserId);
        }
        // 2b: activation hook. Any transition INTO ACTIVE from any state (PLANNING, ON_HOLD,
        // AT_RISK, DELAYED) captures the baseline if one doesn't exist yet.
        if (project.getStatus() == ProjectStatus.ACTIVE && oldStatus != ProjectStatus.ACTIVE
                && project.getBaselinedAt() == null && newStart != null && newEnd != null) {
            project.setStartDate(newStart);
            project.setEndDate(newEnd);
            applyBaseline(project, currentUserId, false);   // false = never overwrite
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
        if (req.getReviewRequired() != null) project.setReviewRequired(req.getReviewRequired());
        if (project.getStatus() == ProjectStatus.COMPLETED) project.setProgressPct(100);
        Project saved = projectRepository.save(project);
        if (req.getOwnerId() != null) ensureManager(saved.getId(), saved.getOwnerId());
        // STEP 8: every project that starts gets the closure checklist (no-op when it already has items)
        if (oldStatus != saved.getStatus() && saved.getStatus() == ProjectStatus.ACTIVE) {
            closureService.ensureDefaults(saved.getId(), currentUserId);
        }
        // STEP 8: tell the team the project is done
        if (oldStatus != ProjectStatus.COMPLETED && saved.getStatus() == ProjectStatus.COMPLETED) {
            closureService.notifyCompleted(saved, currentUserId);
        }
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

    /** STEP 8: Super Admin only (the controller checks). Needs a reason; leaves a trail and tells the team. */
    @Override
    @Transactional
    public ProjectResponse reopenProject(Long id, String reason, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getStatus() != ProjectStatus.COMPLETED)
            throw new IllegalArgumentException("Only a completed project can be reopened.");
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 10)
            throw new IllegalArgumentException("Please say why you are reopening this project (at least 10 characters).");
        if (why.length() > 500) why = why.substring(0, 500);

        project.setStatus(ProjectStatus.ACTIVE);
        project.setReopenedAt(LocalDateTime.now());
        project.setReopenedBy(currentUserId);
        project.setReopenReason(why);
        Project saved = projectRepository.save(project);

        logActivity(id, "REOPENED", currentUserId, "COMPLETED", "ACTIVE",
                "reopened project " + saved.getName() + ": " + why);
        closureService.notifyReopened(saved, currentUserId, why);
        return projectMapper.toResponse(saved, resolveClientName(saved.getClientId()));
    }

    // ---------------------------------------------------------------- rules

    /** Status move check using the project's saved dates (archive and other callers). */
    private void checkTransition(Project project, ProjectStatus from, ProjectStatus to) {
        checkTransition(project, from, to, project.getStartDate(), project.getEndDate());
    }

    /** STEP 3: status move check; the dates are the ones that will be saved with this move. */
    private void checkTransition(Project project, ProjectStatus from, ProjectStatus to,
                                 LocalDate startDate, LocalDate endDate) {
        if (from == ProjectStatus.COMPLETED && to == ProjectStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "A completed project can only be reopened by a Super Admin (Closure panel → Reopen project).");
        }
        if (!FLOW.getOrDefault(from, EnumSet.noneOf(ProjectStatus.class)).contains(to)) {
            throw new IllegalArgumentException("A project cannot move from " + from + " to " + to + ".");
        }
        // 2c: kick-off gate on every first activation, not just PLANNING → ACTIVE.
        // (PLANNING → ON_HOLD → ACTIVE must also pass the gate.)
        if (to == ProjectStatus.ACTIVE && project.getBaselinedAt() == null) {
            checkKickoff(project, startDate, endDate);
        }
        if (to == ProjectStatus.COMPLETED) {
            List<String> blockers = closureService.blockers(project);
            if (!blockers.isEmpty()) {
                throw new IllegalArgumentException("Cannot complete the project: " + String.join("; ", blockers) + ".");
            }
        }
    }

    /**
     * STEP 3 — kick-off gate. A project may only start when it has dates and somebody to do the work.
     * The baseline is saved automatically right after this check passes (see updateProject), so
     * having dates guarantees a baseline.
     */
    private void checkKickoff(Project project, LocalDate startDate, LocalDate endDate) {
        List<String> missing = new ArrayList<>();
        if (startDate == null || endDate == null) {
            missing.add("set the start and end dates");
        }
        boolean hasWorker = projectMemberRepository.findByProjectId(project.getId()).stream()
                .anyMatch(m -> m.getRole() == ProjectRole.MEMBER || m.getRole() == ProjectRole.LEAD);
        if (!hasWorker) {
            missing.add("add at least one team member (Member or Lead)");
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("The project can't start yet. Please " + String.join(" and ", missing) + ".");
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
        return saveBaseline(id, currentUserId, null);
    }

    /** First capture: no reason needed. Already baselined: reason required, history row written. */
    @Override
    @Transactional
    public ProjectResponse saveBaseline(Long id, Long currentUserId, String reason) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getBaselinedAt() != null) {
            return rebaseline(id, reason, currentUserId);
        }
        return doSaveBaseline(project, currentUserId, null);
    }

    @Override
    @Transactional
    public ProjectResponse rebaseline(Long id, String reason, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (project.getBaselinedAt() == null) {
            return doSaveBaseline(project, currentUserId, null);
        }
        String why = reason == null ? "" : reason.trim();
        if (why.length() < 10)
            throw new BusinessRuleException("Please say why you are re-baselining (at least 10 characters).");
        if (why.length() > 1000) why = why.substring(0, 1000);
        assertBaselineEditable(project);
        archiveCurrentBaseline(project, why, null);          // writes ProjectBaselineHistory version N+1
        return doSaveBaseline(project, currentUserId, why);
    }

    private void assertBaselineEditable(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.ARCHIVED)
            throw new BusinessRuleException("This project is completed or archived, so its baseline is locked.");
        if (project.getStartDate() == null || project.getEndDate() == null)
            throw new IllegalArgumentException("Set the start and end dates before saving a baseline.");
    }

    /** Single place that writes the baseline and ONE activity row. */
    private ProjectResponse doSaveBaseline(Project project, Long userId, String reason) {
        assertBaselineEditable(project);
        boolean replacing = project.getBaselinedAt() != null;
        // 2a: applyBaseline now returns the number of task baselines written, so we can
        // report it in the activity log.
        int n = applyBaseline(project, userId, true);
        Project saved = projectRepository.save(project);
        logActivity(saved.getId(), replacing ? "REBASELINED" : "BASELINED", userId, null, null,
                (replacing ? "re-baselined " : "saved a baseline for ") + saved.getName()
                        + " (" + saved.getStartDate() + " → " + saved.getEndDate()
                        + ", budget " + saved.getBudgetTotal() + ", " + n + " task(s))"
                        + (reason == null ? "" : ": " + reason));
        return projectMapper.toResponse(saved, resolveClientName(saved.getClientId()));
    }

    /**
     * Project dates/budget snapshot, then task snapshot via {@link ProjectVarianceService#captureBaseline}.
     * First capture never overwrites existing task baselines (tasks added later stay "added after baseline").
     * Re-baseline archives the previous snapshot into {@link ProjectBaselineHistory}.
     *
     * Note: history rows are written by callers that need them (e.g. {@code rebaseline},
     * {@code ChangeRequestService}) — not here — to avoid duplicate rows.
     *
     * 2a: returns the number of task baselines written.
     */
    private int applyBaseline(Project p, Long userId, boolean overwriteTasks) {
        p.setBaselineStart(p.getStartDate());
        p.setBaselineEnd(p.getEndDate());
        p.setBaselineBudget(p.getBudgetTotal());
        p.setBaselinedAt(LocalDateTime.now());
        p.setBaselinedBy(userId);
        return varianceService.captureBaseline(p.getId(), overwriteTasks, userId);
    }

    private void archiveCurrentBaseline(Project p, String reason, Long changeRequestId) {
        baselineHistoryRepository.save(ProjectBaselineHistory.builder()
                .projectId(p.getId())
                .versionNo((int) baselineHistoryRepository.countByProjectId(p.getId()) + 1)
                .baselineStart(p.getBaselineStart())
                .baselineEnd(p.getBaselineEnd())
                .baselineBudget(p.getBaselineBudget())
                .baselinedAt(p.getBaselinedAt())
                .baselinedBy(p.getBaselinedBy())
                .supersededAt(LocalDateTime.now())
                .changeRequestId(changeRequestId)
                .reason(reason)
                .build());
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