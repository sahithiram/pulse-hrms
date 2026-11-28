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
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
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
import com.mentis.hrms.pm.service.ProjectTeamService;          // NEW
import com.mentis.hrms.pm.service.ProjectVarianceService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;   // CHANGED (was fully-qualified inline)
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;   // NEW
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceImpl implements ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectTeamService projectTeamService;   // NEW
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
        FLOW.put(ProjectStatus.COMPLETED, EnumSet.of(ProjectStatus.ARCHIVED));
        FLOW.put(ProjectStatus.ARCHIVED, EnumSet.of(ProjectStatus.PLANNING));
    }

    @Override
    @Transactional
    public ProjectResponse createProject(CreateProjectRequest req, Long currentUserId) {
        validateDates(req.getStartDate(), req.getEndDate());
        validateClient(req.getClientId());

        // CHANGED: auto-generate PRJ-NNNN instead of a random UUID suffix
        String code = req.getCode() == null ? "" : req.getCode().trim();
        if (code.isEmpty()) {
            code = nextProjectCode();
        }
        if (projectRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Project code already exists: " + code);
        }

        Project project = projectMapper.toEntity(req, currentUserId);
        project.setCode(code);

        Project saved;
        try {
            saved = projectRepository.save(project);
        } catch (DataIntegrityViolationException e) {
            // CHANGED: only treat as duplicate when the unique index on "code" fired.
            // A bad ownerId / teamId also throws DataIntegrityViolationException; do not
            // misreport those as "Project code already exists".
            String msg = e.getMostSpecificCause() == null ? null : e.getMostSpecificCause().getMessage();
            if (msg != null && msg.toLowerCase().contains("code")) {
                throw new DuplicateResourceException("Project code already exists: " + code);
            }
            throw e;
        }

        // NEW: multi-team. If teamIds was supplied, honour it; otherwise fall back to teamId.
        List<Long> wantedTeams = (req.getTeamIds() != null && !req.getTeamIds().isEmpty())
                ? req.getTeamIds()
                : (req.getTeamId() != null ? List.of(req.getTeamId()) : List.of());
        projectTeamService.replace(saved, wantedTeams);

        addInitialMembers(saved, currentUserId, req.getMemberIds());
        applyManagers(saved.getId(), saved.getOwnerId(), req.getManagerIds(), currentUserId);   // PM-025
        logActivity(saved.getId(), "CREATED", currentUserId, null, saved.getStatus().name(),
                "created project " + saved.getName());
        log.info("Project created id={} code={}", saved.getId(), saved.getCode());
        return respond(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public String nextProjectCode() {                              // NEW
        // Database-independent: read the PRJ-… codes and take the highest number in Java.
        long max = 1000;   // first generated code is PRJ-1001
        for (String c : projectRepository.findAllAutoCodes()) {
            if (c != null && c.matches("PRJ-\\d+")) {
                try { max = Math.max(max, Long.parseLong(c.substring(4))); } catch (NumberFormatException ignore) { }
            }
        }
        long n = max + 1;
        String code = "PRJ-" + n;
        // Defensive: covers the (rare) case where a code outside the numeric pattern
        // was inserted between the MAX query and this loop.
        while (projectRepository.existsByCode(code)) {
            code = "PRJ-" + (++n);
        }
        return code;
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
            checkTransition(project, oldStatus, req.getStatus(), newStart, newEnd);
        }

        if (req.getName() != null) project.setName(req.getName());
        if (req.getDescription() != null) project.setDescription(req.getDescription());
        if (req.getOwnerId() != null) project.setOwnerId(req.getOwnerId());
        if (req.getDepartmentId() != null) project.setDepartmentId(req.getDepartmentId());
        if (req.getClientId() != null) project.setClientId(req.getClientId());

        // CHANGED: multi-team update. If teamIds is present (even empty), it replaces
        // the team set. Otherwise, an older single-team client's teamId is added
        // without dropping the others; teamId=0 clears the set.
        if (req.getTeamIds() != null) {
            projectTeamService.replace(project, req.getTeamIds());
        } else if (req.getTeamId() != null) {
            if (req.getTeamId() == 0L) {
                projectTeamService.replace(project, List.of());
            } else {
                LinkedHashSet<Long> all = new LinkedHashSet<>();
                all.add(req.getTeamId());
                all.addAll(projectTeamService.teamIdsOf(project.getId()));
                projectTeamService.replace(project, all);
            }
        }

        if (req.getStatus() != null) project.setStatus(req.getStatus());
        if (oldStatus != ProjectStatus.COMPLETED && project.getStatus() == ProjectStatus.COMPLETED) {
            project.setCompletedAt(LocalDateTime.now());
            project.setCompletedBy(currentUserId);
        }
        if (project.getStatus() == ProjectStatus.ACTIVE && oldStatus != ProjectStatus.ACTIVE
                && project.getBaselinedAt() == null && newStart != null && newEnd != null) {
            project.setStartDate(newStart);
            project.setEndDate(newEnd);
            applyBaseline(project, currentUserId, false);
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
        if (oldStatus != saved.getStatus() && saved.getStatus() == ProjectStatus.ACTIVE) {
            closureService.ensureDefaults(saved.getId(), currentUserId);
        }
        if (oldStatus != ProjectStatus.COMPLETED && saved.getStatus() == ProjectStatus.COMPLETED) {
            closureService.notifyCompleted(saved, currentUserId);
        }
        if (oldStatus != saved.getStatus()) {
            logActivity(id, "STATUS_CHANGED", currentUserId, oldStatus.name(), saved.getStatus().name(),
                    "changed status of " + saved.getName() + " from " + oldStatus + " to " + saved.getStatus());
        } else {
            logActivity(id, "UPDATED", currentUserId, null, null, "updated project " + saved.getName());
        }
        return respond(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectResponse getProject(Long id) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        return respond(project);
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
        // NEW: batch-load all teams for the whole page in one query
        Map<Long, List<Long>> teamsByProject = projectTeamService.teamIdsByProject(page.getContent());
        Page<ProjectSummaryResponse> mapped = page.map(p -> {
            ProjectSummaryResponse r = projectMapper.toSummary(p, clientNameMap.get(p.getClientId()));
            r.setTeamIds(teamsByProject.getOrDefault(p.getId(), List.of()));
            return r;
        });
        return PageResponse.of(mapped);
    }

    /** NEW: Project -> response, including all of its teams. */
    private ProjectResponse respond(Project p) {
        ProjectResponse r = projectMapper.toResponse(p, resolveClientName(p.getClientId()));
        r.setTeamIds(new ArrayList<>(projectTeamService.teamIdsOf(p.getId())));
        decorateMembers(r);                     // PM-025: ownerName / managers / leadName
        return r;
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
        return respond(saved);
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
        logActivity(id, "DELETED", currentUserId, null, null, "deleted project " + project.getName());
        // NEW: remove project-team links (avoid orphan rows)
        projectTeamService.replace(project, List.of());
        closureService.deleteForProject(id);
        projectMemberRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
        log.info("Project deleted id={}", id);
    }

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
        return respond(saved);
    }

    // ---------------------------------------------------------------- rules

    private void checkTransition(Project project, ProjectStatus from, ProjectStatus to) {
        checkTransition(project, from, to, project.getStartDate(), project.getEndDate());
    }

    private void checkTransition(Project project, ProjectStatus from, ProjectStatus to,
                                 LocalDate startDate, LocalDate endDate) {
        if (from == ProjectStatus.COMPLETED && to == ProjectStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "A completed project can only be reopened by a Super Admin (Closure panel → Reopen project).");
        }
        if (!FLOW.getOrDefault(from, EnumSet.noneOf(ProjectStatus.class)).contains(to)) {
            throw new IllegalArgumentException("A project cannot move from " + from + " to " + to + ".");
        }
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

    /**
     * PM-025: co-managers. Adds (or promotes) a MANAGER row for every id in managerIds.
     * The owner and the creator are already MANAGERs, so they are skipped here.
     * Unknown ids are ignored on purpose: the UI passes a multi-select and a stale id
     * must not make project creation fail.
     */
    @Override
    @Transactional
    public void applyManagers(Long projectId, Long ownerId, List<Long> managerIds, Long actorId) {
        if (projectId == null || managerIds == null || managerIds.isEmpty()) return;
        for (Long empId : new java.util.LinkedHashSet<>(managerIds)) {
            if (empId == null || empId.equals(ownerId)) continue;
            if (!employeeRepository.existsById(empId)) continue;
            ensureManager(projectId, empId);
        }
        logActivity(projectId, "UPDATED", actorId, null,
                "managers=" + managerIds, "set co-managers on the project");
    }

    /** PM-025: fill the display fields (owner name, managers, lead) on a single response. */
    @Transactional(readOnly = true)
    public ProjectResponse decorateMembers(ProjectResponse r) {
        if (r == null || r.getId() == null) return r;
        if (r.getOwnerId() != null) {
            employeeRepository.findById(r.getOwnerId())
                    .ifPresent(e -> r.setOwnerName(fullName(e)));
        }
        java.util.List<ProjectResponse.MemberRef> managers = new java.util.ArrayList<>();
        String leadName = null;
        for (ProjectMember m : projectMemberRepository.findByProjectId(r.getId())) {
            if (m.getRole() == ProjectRole.MANAGER && !java.util.Objects.equals(m.getEmployeeId(), r.getOwnerId())) {
                employeeRepository.findById(m.getEmployeeId()).ifPresent(e ->
                        managers.add(ProjectResponse.MemberRef.builder()
                                .id(e.getId()).employeeId(e.getEmployeeId())
                                .name(fullName(e)).initials(initials(e))
                                .role("MANAGER").allocationPct(m.getAllocationPct()).build()));
            } else if (m.getRole() == ProjectRole.LEAD && leadName == null) {
                leadName = employeeRepository.findById(m.getEmployeeId())
                        .map(this::fullName).orElse(null);
            }
        }
        r.setManagers(managers);
        r.setLeadName(leadName);
        return r;
    }

    private String fullName(com.mentis.hrms.model.Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName().trim();
        String last  = e.getLastName() == null ? "" : e.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank()) return e.getEmployeeName();
        return e.getEmail() != null ? e.getEmail() : ("Employee #" + e.getId());
    }

    private String initials(com.mentis.hrms.model.Employee e) {
        String n = fullName(e);
        String[] parts = n.trim().split("\\s+");
        String a = parts.length > 0 && !parts[0].isEmpty() ? parts[0].substring(0, 1) : "";
        String b = parts.length > 1 && !parts[1].isEmpty() ? parts[1].substring(0, 1) : "";
        String out = (a + b).toUpperCase();
        return out.isEmpty() ? "?" : out;
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
        archiveCurrentBaseline(project, why, null);
        return doSaveBaseline(project, currentUserId, why);
    }

    private void assertBaselineEditable(Project project) {
        if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.ARCHIVED)
            throw new BusinessRuleException("This project is completed or archived, so its baseline is locked.");
        if (project.getStartDate() == null || project.getEndDate() == null)
            throw new IllegalArgumentException("Set the start and end dates before saving a baseline.");
    }

    private ProjectResponse doSaveBaseline(Project project, Long userId, String reason) {
        assertBaselineEditable(project);
        boolean replacing = project.getBaselinedAt() != null;
        int n = applyBaseline(project, userId, true);
        Project saved = projectRepository.save(project);
        logActivity(saved.getId(), replacing ? "REBASELINED" : "BASELINED", userId, null, null,
                (replacing ? "re-baselined " : "saved a baseline for ") + saved.getName()
                        + " (" + saved.getStartDate() + " → " + saved.getEndDate()
                        + ", budget " + saved.getBudgetTotal() + ", " + n + " task(s))"
                        + (reason == null ? "" : ": " + reason));
        return respond(saved);
    }

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

    /**
     * PM-026: hand a project over to another owner.
     * Rules: Super Admin always; otherwise the current owner.
     * The previous owner is kept as MANAGER unless keepPreviousAsManager=false,
     * so nothing he was working on disappears from his list mid-flight.
     */
    @Override
    @Transactional
    public ProjectResponse transferOwner(Long id, Long newOwnerId, String reason,
                                         boolean keepPreviousAsManager, Long currentUserId) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + id));
        if (newOwnerId == null) throw new IllegalArgumentException("Pick the new project owner.");
        if (project.getStatus() == ProjectStatus.COMPLETED || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("This project is finished, so its owner can't be changed.");
        }
        boolean isSuper = employeeRepository.findById(currentUserId)
                .map(e -> e.getRole() == com.mentis.hrms.model.enums.UserRole.SUPER_ADMIN).orElse(false);
        boolean isCurrentOwner = java.util.Objects.equals(project.getOwnerId(), currentUserId);
        if (!isSuper && !isCurrentOwner) {
            throw new PmAccessDeniedException("Only the current owner or a Super Admin can hand this project over.");
        }
        var newOwner = employeeRepository.findById(newOwnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + newOwnerId));
        Long previous = project.getOwnerId();
        project.setOwnerId(newOwnerId);
        Project saved = projectRepository.save(project);
        ensureManager(id, newOwnerId);
        if (keepPreviousAsManager && previous != null && !previous.equals(newOwnerId)) {
            ensureManager(id, previous);
        }
        logActivity(id, "OWNER_CHANGED", currentUserId,
                previous == null ? null : String.valueOf(previous), String.valueOf(newOwnerId),
                "handed the project over to " + fullName(newOwner)
                        + (reason == null || reason.isBlank() ? "" : " — " + reason));
        return respond(saved);   // respond() already decorates ownerName / managers / leadName
    }

}
