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
    public PageResponse<TeamSummaryResponse> listTeams(String search, Pageable pageable) {
        Page<Team> page = teamRepository.search(search, pageable);
        return PageResponse.of(page.map(this::toSummaryWithLeadName));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> listAllForCards() {
        return teamRepository.findAll().stream()
                .map(this::toSummaryWithLeadName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamSummaryResponse> listForDropdown() {
        return teamRepository.findAll().stream()
                .sorted(Comparator.comparing(Team::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toSummaryWithLeadName)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteTeam(Long id) {
        if (!teamRepository.existsById(id))
            throw new ResourceNotFoundException("Team not found: " + id);
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

    /**
     * Team tasks = union of:
     *   (A) Tasks explicitly tagged with teamId
     *   (B) Tasks assigned to any member of the team
     * Sorted: open first, then newest.
     *
     * FIX 5: Query already filters correctly — no code change required.
     */
    @Override
    @Transactional(readOnly = true)
    public TeamTasksResult listTeamTasks(Long teamId, int limit) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

        List<Long> memberIds = teamMemberRepository.findByTeamId(teamId)
                .stream().map(TeamMember::getEmployeeId).collect(Collectors.toList());

        // (A) explicit team tasks
        Set<Long> seen = new HashSet<>();
        List<Task> combined = new ArrayList<>();

        for (Task t : taskRepository.findByTeamId(teamId)) {
            if (seen.add(t.getId())) combined.add(t);
        }

        // (B) tasks assigned to any team member
        if (!memberIds.isEmpty()) {
            for (Task t : taskRepository.findAllByAssigneeIn(memberIds)) {
                if (seen.add(t.getId())) combined.add(t);
            }
        }

        // sort: open first, then due date asc, then createdAt desc
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

        List<TaskSummaryResponse> summaries = combined.stream()
                .limit(limit > 0 ? limit : 100)
                .map(this::toTaskSummary)
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

    /**
     * FIX 7: Defensive name resolution loop — any single employee lookup failure
     * must not break the entire task summary response.
     */
    private TaskSummaryResponse toTaskSummary(Task t) {
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

        String projectName = t.getProjectId() != null
                ? projectRepository.findById(t.getProjectId()).map(p -> p.getName()).orElse(null)
                : null;

        return TaskSummaryResponse.builder()
                .id(t.getId()).title(t.getTitle())
                .status(t.getStatus()).priority(t.getPriority())
                .dueDate(t.getDueDate()).progressPct(t.getProgressPct())
                .projectId(t.getProjectId()).projectName(projectName)
                .teamId(t.getTeamId())
                .parentTaskId(t.getParentTaskId())
                .assigneeIds(ids).assigneeNames(names)
                .build();
    }

    /**
     * FIX 4: Null-safe employee name resolution.
     * Order of preference: firstName + lastName → employeeName → email → "Employee #id".
     * Never returns null; never throws.
     */
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