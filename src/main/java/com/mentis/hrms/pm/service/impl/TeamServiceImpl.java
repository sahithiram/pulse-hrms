package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreateTeamRequest;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.dto.response.TeamResponse;
import com.mentis.hrms.pm.dto.response.TeamSummaryResponse;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskAssignment;
import com.mentis.hrms.pm.entity.Team;
import com.mentis.hrms.pm.entity.TeamMember;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.mapper.TeamMapper;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.pm.service.ProjectTeamService;             // NEW
import com.mentis.hrms.pm.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageImpl;
import java.util.*;
import java.util.stream.Collectors;
import com.mentis.hrms.pm.dto.request.AddTeamMemberRequest;
import com.mentis.hrms.pm.dto.request.UpdateTeamMemberRequest;
import com.mentis.hrms.pm.dto.response.TeamMemberResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class TeamServiceImpl implements TeamService {

    private final TeamRepository teamRepository;
    private final ProjectTeamService projectTeamService;             // NEW
    private final TeamMemberRepository teamMemberRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final ProjectRepository projectRepository;
    private final TeamMapper teamMapper;
    private final com.mentis.hrms.repository.EmployeeRepository employeeRepository;
    private final com.mentis.hrms.pm.security.PmAccessService access;

    @Override
    @Transactional
    public TeamResponse createTeam(CreateTeamRequest req, Long userId) {
        if (teamRepository.existsByName(req.getName()))
            throw new DuplicateResourceException("Team name already exists: " + req.getName());
        Team saved = teamRepository.save(teamMapper.toEntity(req, userId));
        if (req.getMemberIds() != null) {
            for (Long empId : req.getMemberIds()) {
                teamMemberRepository.save(TeamMember.builder()
                        .teamId(saved.getId()).employeeId(empId)
                        .roleInTeam("Developer").allocationPct(100).build());
            }
        }
        return toResponseWithMembers(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public TeamResponse getTeam(Long id) {
        Team t = teamRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + id));
        return toResponseWithMembers(t);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TeamSummaryResponse> listTeams(String search, Pageable pageable, Set<Long> onlyTeamIds) {
        if (onlyTeamIds == null) {
            Page<Team> page = teamRepository.search(search, pageable);
            return PageResponse.of(new PageImpl<>(toSummaries(page.getContent()), pageable, page.getTotalElements()));
        }
        String q = search == null ? "" : search.trim().toLowerCase();
        List<Team> all = teamsInScope(onlyTeamIds).stream()
                .filter(t -> q.isEmpty() || (t.getName() != null && t.getName().toLowerCase().contains(q)))
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return PageResponse.of(new PageImpl<>(
                toSummaries(new ArrayList<>(all.subList(from, to))), pageable, all.size()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> listAllForCards(Set<Long> onlyTeamIds) {
        return toSummaries(teamsInScope(onlyTeamIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> listForDropdown(Set<Long> onlyTeamIds) {
        return toSummaries(teamsInScope(onlyTeamIds).stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList()));
    }

    /** null = every team, empty = none, otherwise only those ids. */
    private List<Team> teamsInScope(Set<Long> onlyTeamIds) {
        if (onlyTeamIds == null) return teamRepository.findAll();
        if (onlyTeamIds.isEmpty()) return List.of();
        return teamRepository.findAllById(onlyTeamIds);
    }

    @Override
    @Transactional
    public void deleteTeam(Long id) {
        if (!teamRepository.existsById(id))
            throw new ResourceNotFoundException("Team not found: " + id);

        long activeTasks = taskRepository.countByTeamIdAndStatusNot(id, TaskStatus.DONE);
        if (activeTasks > 0) {
            throw new BusinessRuleException("Cannot delete team. It has " + activeTasks + " active tasks attached.");
        }

        teamMemberRepository.deleteByTeamId(id);
        projectTeamService.removeTeam(id);   // NEW: drop project↔team links
        teamRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> listTeamMemberIds(Long teamId) {
        if (!teamRepository.existsById(teamId))
            throw new ResourceNotFoundException("Team not found: " + teamId);
        return teamMemberRepository.findByTeamId(teamId)
                .stream().map(TeamMember::getEmployeeId).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TeamTasksResult listTeamTasks(Long teamId, int limit, Long viewerId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        List<Long> memberIds = teamMemberRepository.findByTeamId(teamId)
                .stream().map(TeamMember::getEmployeeId).collect(Collectors.toList());

        Set<Long> seen = new HashSet<>();
        List<Task> combined = new ArrayList<>();

        for (Task t : taskRepository.findByTeamId(teamId)) {
            if (seen.add(t.getId())) combined.add(t);
        }
        if (!memberIds.isEmpty()) {
            for (Task t : taskRepository.findAllByAssigneeIn(memberIds)) {
                if (seen.add(t.getId())) combined.add(t);
            }
        }

        combined.sort((a, b) -> {
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

        long open = combined.stream().filter(t -> t.getStatus() != TaskStatus.DONE).count();
        long done = combined.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();

        // PERF: load assignees, employee names and project names for ALL tasks in 3 queries
        // (was: 2-4 queries per task, i.e. hundreds of round trips for limit=300).
        List<Task> pageTasks = combined.stream()
                .limit(limit > 0 ? limit : 100)
                .collect(Collectors.toList());
        List<TaskSummaryResponse> summaries = toTaskSummaries(pageTasks, viewerId);

        String leadName = team.getLeadEmployeeId() != null
                ? resolveEmployeeName(team.getLeadEmployeeId()) : null;

        return new TeamTasksResult(
                team.getId(), team.getName(),
                team.getLeadEmployeeId(), leadName,
                memberIds, open, done, summaries);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listTeamMembers(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        List<TeamMember> members = teamMemberRepository.findByTeamId(teamId);
        // PERF: one query for all member employees instead of one per member
        Map<Long, com.mentis.hrms.model.Employee> emps = new HashMap<>();
        Set<Long> ids = members.stream().map(TeamMember::getEmployeeId).collect(Collectors.toSet());
        if (!ids.isEmpty()) employeeRepository.findAllById(ids).forEach(e -> emps.put(e.getId(), e));
        return members.stream()
                .map(m -> toMemberResponse(team, m, emps))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public TeamMemberResponse addMember(Long teamId, AddTeamMemberRequest req, Long userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        if (!employeeRepository.existsById(req.getEmployeeId()))
            throw new ResourceNotFoundException("Employee not found: " + req.getEmployeeId());
        if (teamMemberRepository.findByTeamIdAndEmployeeId(teamId, req.getEmployeeId()).isPresent())
            throw new DuplicateResourceException("Already a member of this team");
        TeamMember saved = teamMemberRepository.save(TeamMember.builder()
                .teamId(teamId).employeeId(req.getEmployeeId())
                .roleInTeam(req.getRoleInTeam() == null || req.getRoleInTeam().isBlank()
                        ? "Developer" : req.getRoleInTeam().trim())
                .allocationPct(req.getAllocationPct() == null ? 100 : req.getAllocationPct())
                .build());
        return toMemberResponse(team, saved);
    }

    @Override
    @Transactional
    public TeamMemberResponse updateMember(Long teamId, Long employeeId, UpdateTeamMemberRequest req) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));
        TeamMember m = teamMemberRepository.findByTeamIdAndEmployeeId(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not in this team: " + employeeId));
        if (req.getRoleInTeam() != null && !req.getRoleInTeam().isBlank()) m.setRoleInTeam(req.getRoleInTeam().trim());
        if (req.getAllocationPct() != null) m.setAllocationPct(req.getAllocationPct());
        return toMemberResponse(team, teamMemberRepository.save(m));
    }

    @Override
    @Transactional
    public void removeMember(Long teamId, Long employeeId) {
        TeamMember m = teamMemberRepository.findByTeamIdAndEmployeeId(teamId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not in this team: " + employeeId));
        teamMemberRepository.delete(m);
    }

    private TeamMemberResponse toMemberResponse(Team team, TeamMember m,
                                                Map<Long, com.mentis.hrms.model.Employee> emps) {
        com.mentis.hrms.model.Employee e = emps.get(m.getEmployeeId());
        return TeamMemberResponse.builder()
                .employeeId(m.getEmployeeId())
                .employeeName(e == null ? "Employee #" + m.getEmployeeId() : displayName(e))
                .employeeCode(e == null ? null : e.getEmployeeId())
                .roleInTeam(m.getRoleInTeam())
                .allocationPct(m.getAllocationPct())
                .lead(team.getLeadEmployeeId() != null && team.getLeadEmployeeId().equals(m.getEmployeeId()))
                .build();
    }

    private static String displayName(com.mentis.hrms.model.Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName().trim();
        String last = e.getLastName() == null ? "" : e.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank()) return e.getEmployeeName().trim();
        if (e.getEmail() != null && !e.getEmail().isBlank()) return e.getEmail().trim();
        return "Employee #" + e.getId();
    }

    /** PERF: summaries for many tasks with a fixed number of queries. */
    private List<TaskSummaryResponse> toTaskSummaries(List<Task> tasks, Long viewerId) {
        if (tasks.isEmpty()) return new ArrayList<>();
        List<Long> taskIds = tasks.stream().map(Task::getId).collect(Collectors.toList());

        Map<Long, List<Long>> assigneesByTask = new HashMap<>();
        for (TaskAssignment a : taskAssignmentRepository.findByTaskIdIn(taskIds))
            assigneesByTask.computeIfAbsent(a.getTaskId(), k -> new ArrayList<>()).add(a.getEmployeeId());

        Set<Long> empIds = assigneesByTask.values().stream()
                .flatMap(List::stream).collect(Collectors.toSet());
        Map<Long, String> empNames = new HashMap<>();
        if (!empIds.isEmpty())
            employeeRepository.findAllById(empIds).forEach(e -> empNames.put(e.getId(), displayName(e)));

        Set<Long> projectIds = tasks.stream().map(Task::getProjectId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> projectNames = new HashMap<>();
        if (!projectIds.isEmpty())
            projectRepository.findAllById(projectIds).forEach(p -> projectNames.put(p.getId(), p.getName()));

        Map<Long, Boolean> visible = new HashMap<>();   // canViewProject is asked once per distinct project
        List<TaskSummaryResponse> out = new ArrayList<>(tasks.size());
        for (Task t : tasks) {
            List<Long> ids = assigneesByTask.getOrDefault(t.getId(), List.of());
            List<String> names = new ArrayList<>(ids.size());
            for (Long id : ids) names.add(empNames.getOrDefault(id, "Employee #" + id));
            boolean hidden = viewerId != null && t.getProjectId() != null
                    && !visible.computeIfAbsent(t.getProjectId(), pid -> access.canViewProject(viewerId, pid));
            out.add(TaskSummaryResponse.builder()
                    .id(t.getId())
                    .title(hidden ? "Task in another project" : t.getTitle())
                    .status(t.getStatus()).priority(t.getPriority())
                    .dueDate(t.getDueDate()).progressPct(t.getProgressPct())
                    .projectId(hidden ? null : t.getProjectId())
                    .projectName(hidden ? "Another project" : projectNames.get(t.getProjectId()))
                    .teamId(t.getTeamId())
                    .parentTaskId(t.getParentTaskId())
                    .assigneeIds(ids).assigneeNames(names)
                    .build());
        }
        return out;
    }

    private TeamMemberResponse toMemberResponse(Team team, TeamMember m) {
        String code = null;
        try {
            code = employeeRepository.findById(m.getEmployeeId()).map(e -> e.getEmployeeId()).orElse(null);
        } catch (Exception ignored) { }
        return TeamMemberResponse.builder()
                .employeeId(m.getEmployeeId())
                .employeeName(resolveEmployeeName(m.getEmployeeId()))
                .employeeCode(code)
                .roleInTeam(m.getRoleInTeam())
                .allocationPct(m.getAllocationPct())
                .lead(team.getLeadEmployeeId() != null && team.getLeadEmployeeId().equals(m.getEmployeeId()))
                .build();
    }

    private TeamResponse toResponseWithMembers(Team t) {
        List<Long> ids = teamMemberRepository.findByTeamId(t.getId())
                .stream().map(TeamMember::getEmployeeId).collect(Collectors.toList());
        return teamMapper.toResponse(t, ids);
    }

    /** PERF: member counts and lead names for many teams in 2 queries instead of 2 per team. */
    private List<TeamSummaryResponse> toSummaries(List<Team> teams) {
        if (teams == null || teams.isEmpty()) return new ArrayList<>();
        Set<Long> idSet = teams.stream().map(Team::getId).collect(Collectors.toSet());
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : teamMemberRepository.countByTeamIds(idSet))
            counts.put((Long) row[0], ((Number) row[1]).longValue());
        Set<Long> leadIds = teams.stream().map(Team::getLeadEmployeeId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> leadNames = new HashMap<>();
        if (!leadIds.isEmpty()) {
            employeeRepository.findAllById(leadIds).forEach(e -> {
                String first = e.getFirstName() == null ? "" : e.getFirstName().trim();
                String last = e.getLastName() == null ? "" : e.getLastName().trim();
                String full = (first + " " + last).trim();
                if (full.isEmpty() && e.getEmployeeName() != null && !e.getEmployeeName().isBlank())
                    full = e.getEmployeeName().trim();
                if (full.isEmpty() && e.getEmail() != null) full = e.getEmail();
                leadNames.put(e.getId(), full.isEmpty() ? "Employee #" + e.getId() : full);
            });
        }
        List<TeamSummaryResponse> out = new ArrayList<>(teams.size());
        for (Team t : teams) {
            Long lead = t.getLeadEmployeeId();
            out.add(teamMapper.toSummary(t, counts.getOrDefault(t.getId(), 0L),
                    lead == null ? null : leadNames.getOrDefault(lead, "Employee #" + lead)));
        }
        return out;
    }

    private TeamSummaryResponse toSummaryWithLeadName(Team t) {
        Long count = teamMemberRepository.countByTeamId(t.getId());
        String leadName = t.getLeadEmployeeId() != null
                ? resolveEmployeeName(t.getLeadEmployeeId()) : null;
        return teamMapper.toSummary(t, count, leadName);
    }

    private TaskSummaryResponse toTaskSummary(Task t, Long viewerId, Map<Long, Boolean> visible) {
        List<Long> ids = taskAssignmentRepository.findByTaskId(t.getId())
                .stream().map(TaskAssignment::getEmployeeId).collect(Collectors.toList());

        List<String> names = new ArrayList<>(ids.size());
        for (Long id : ids) {
            try { names.add(resolveEmployeeName(id)); }
            catch (Exception ex) { log.warn("Failed to resolve employee name for id={}: {}", id, ex.getMessage()); names.add("Employee #" + id); }
        }

        boolean hidden = viewerId != null && t.getProjectId() != null
                && !visible.computeIfAbsent(t.getProjectId(), pid -> access.canViewProject(viewerId, pid));

        String projectName = t.getProjectId() != null
                ? projectRepository.findById(t.getProjectId()).map(p -> p.getName()).orElse(null)
                : null;

        return TaskSummaryResponse.builder()
                .id(t.getId())
                .title(hidden ? "Task in another project" : t.getTitle())
                .status(t.getStatus()).priority(t.getPriority())
                .dueDate(t.getDueDate()).progressPct(t.getProgressPct())
                .projectId(hidden ? null : t.getProjectId())
                .projectName(hidden ? "Another project" : projectName)
                .teamId(t.getTeamId())
                .parentTaskId(t.getParentTaskId())
                .assigneeIds(ids).assigneeNames(names)
                .build();
    }

    private String resolveEmployeeName(Long id) {
        if (id == null) return null;
        try {
            return employeeRepository.findById(id)
                    .map(e -> {
                        String first = e.getFirstName() == null ? "" : e.getFirstName().trim();
                        String last  = e.getLastName()  == null ? "" : e.getLastName().trim();
                        String full  = (first + " " + last).trim();
                        if (!full.isEmpty()) return full;
                        if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank())
                            return e.getEmployeeName().trim();
                        if (e.getEmail() != null && !e.getEmail().isBlank())
                            return e.getEmail().trim();
                        return "Employee #" + id;
                    })
                    .orElse("Employee #" + id);
        } catch (Exception ex) {
            log.warn("resolveEmployeeName failed for id={}: {}", id, ex.getMessage());
            return "Employee #" + id;
        }
    }
}