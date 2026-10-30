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
            return PageResponse.of(page.map(this::toSummaryWithLeadName));
        }
        String q = search == null ? "" : search.trim().toLowerCase();
        List<Team> all = teamsInScope(onlyTeamIds).stream()
                .filter(t -> q.isEmpty() || (t.getName() != null && t.getName().toLowerCase().contains(q)))
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        Page<Team> page = new PageImpl<>(all.subList(from, to), pageable, all.size());
        return PageResponse.of(page.map(this::toSummaryWithLeadName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> listAllForCards(Set<Long> onlyTeamIds) {
        return teamsInScope(onlyTeamIds).stream()
                .map(this::toSummaryWithLeadName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> listForDropdown(Set<Long> onlyTeamIds) {
        return teamsInScope(onlyTeamIds).stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toSummaryWithLeadName)
                .collect(Collectors.toList());
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

        // GAP 3: Safety Check
        long activeTasks = taskRepository.countByTeamIdAndStatusNot(id, TaskStatus.DONE);
        if (activeTasks > 0) {
            throw new BusinessRuleException("Cannot delete team. It has " + activeTasks + " active tasks attached.");
        }

        teamMemberRepository.deleteByTeamId(id);
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

        Map<Long, Boolean> visible = new HashMap<>();   // one permission check per project, not per task
        List<TaskSummaryResponse> summaries = combined.stream()
                .limit(limit > 0 ? limit : 100)
                .map(t -> toTaskSummary(t, viewerId, visible))
                .collect(Collectors.toList());

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
        return teamMemberRepository.findByTeamId(teamId).stream()
                .map(m -> toMemberResponse(team, m))
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
    // ── helpers ──

    private TeamResponse toResponseWithMembers(Team t) {
        List<Long> ids = teamMemberRepository.findByTeamId(t.getId())
                .stream().map(TeamMember::getEmployeeId).collect(Collectors.toList());
        return teamMapper.toResponse(t, ids);
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
            try {
                names.add(resolveEmployeeName(id));
            } catch (Exception ex) {
                log.warn("Failed to resolve employee name for id={}: {}", id, ex.getMessage());
                names.add("Employee #" + id);
            }
        }

        // GAP 4: hide title and project when the viewer can't open that project.
        // Status, dates and assignees stay, so workload numbers remain correct.
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