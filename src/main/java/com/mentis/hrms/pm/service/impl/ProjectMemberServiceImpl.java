package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.request.AddMemberRequest;
import com.mentis.hrms.pm.dto.request.UpdateMemberRequest;
import com.mentis.hrms.pm.dto.response.ProjectMemberResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.service.ProjectMemberService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.service.NotificationEmitter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.service.ProjectFinanceService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberServiceImpl implements ProjectMemberService {

    private final ProjectMemberRepository repo;
    private final ProjectRepository projectRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationEmitter notificationEmitter;
    private final ProjectFinanceService projectFinanceService;

    // ───────────────────────── list ─────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> list(Long projectId, boolean includeRates) {
        Project p = findProject(projectId);
        List<ProjectMember> rows = repo.findByProjectId(projectId);

        List<Long> ids = rows.stream().map(ProjectMember::getEmployeeId).distinct().collect(Collectors.toList());
        Map<Long, Employee> byId = new HashMap<>();
        employeeRepository.findAllById(ids).forEach(e -> byId.put(e.getId(), e));

        return rows.stream()
                .sorted(Comparator.comparingInt((ProjectMember m) -> m.getRole() == null ? 9 : m.getRole().ordinal())
                        .thenComparing(ProjectMember::getId))
                .map(m -> toResponse(m, p, byId.get(m.getEmployeeId()), includeRates))
                .collect(Collectors.toList());
    }

    // ───────────────────────── add ─────────────────────────

    @Override
    @Transactional
    public ProjectMemberResponse add(Long projectId, AddMemberRequest req, Long actorPk) {
        Project p = findProject(projectId);
        Employee emp = employeeRepository.findById(req.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + req.getEmployeeId()));

        if (repo.findByProjectIdAndEmployeeId(projectId, emp.getId()).isPresent())
            throw new DuplicateResourceException("Already a member of this project");
        assertDates(req.getStartDate(), req.getEndDate());

        ProjectMember saved = repo.save(ProjectMember.builder()
                .projectId(projectId)
                .employeeId(emp.getId())
                .role(req.getRole() == null ? ProjectRole.MEMBER : req.getRole())
                .allocationPct(req.getAllocationPct() == null ? 100 : req.getAllocationPct())
                .hourlyRate(req.getHourlyRate())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .build());

        notifyAdded(p, emp, saved, actorPk);
        return toResponse(saved, p, emp, true);
    }

    // ───────────────────────── update ─────────────────────────

    @Override
    @Transactional
    public ProjectMemberResponse update(Long projectId, Long memberId, UpdateMemberRequest req, Long actorPk) {
        Project p = findProject(projectId);
        ProjectMember m = findMember(projectId, memberId);

        if (req.getRole() != null && req.getRole() != m.getRole()) {
            if (m.getEmployeeId().equals(p.getOwnerId()) && req.getRole() != ProjectRole.MANAGER)
                throw new BusinessRuleException("The project owner must stay a manager");
            m.setRole(req.getRole());
        }
        if (req.getAllocationPct() != null) m.setAllocationPct(req.getAllocationPct());
        if (req.getHourlyRate() != null) m.setHourlyRate(req.getHourlyRate());
        if (req.getStartDate() != null) m.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) m.setEndDate(req.getEndDate());
        assertDates(m.getStartDate(), m.getEndDate());

        ProjectMember saved = repo.save(m);
        if (req.getHourlyRate() != null) projectFinanceService.recalculateCost(projectId);
        return toResponse(saved, p, employeeRepository.findById(saved.getEmployeeId()).orElse(null), true);
    }

    // ───────────────────────── remove ─────────────────────────

    @Override
    @Transactional
    public void remove(Long projectId, Long memberId, Long actorPk) {
        Project p = findProject(projectId);
        ProjectMember m = findMember(projectId, memberId);
        if (m.getEmployeeId().equals(p.getOwnerId()))
            throw new BusinessRuleException("The project owner can't be removed. Change the owner first.");
        repo.delete(m);
        projectFinanceService.recalculateCost(projectId);
    }

    // ───────────────────────── helpers ─────────────────────────

    private Project findProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    private ProjectMember findMember(Long projectId, Long memberId) {
        return repo.findById(memberId)
                .filter(x -> x.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Member not found: " + memberId));
    }

    private void assertDates(LocalDate start, LocalDate end) {
        if (start != null && end != null && end.isBefore(start))
            throw new BusinessRuleException("End date cannot be before the start date");
    }

    private ProjectMemberResponse toResponse(ProjectMember m, Project p, Employee e, boolean includeRates) {
        return ProjectMemberResponse.builder()
                .id(m.getId())
                .projectId(m.getProjectId())
                .employeeId(m.getEmployeeId())
                .employeeName(displayName(e, m.getEmployeeId()))
                .employeeCode(e != null ? e.getEmployeeId() : null)
                .role(m.getRole())
                .allocationPct(m.getAllocationPct())
                .hourlyRate(includeRates ? m.getHourlyRate() : null)
                .startDate(m.getStartDate())
                .endDate(m.getEndDate())
                .owner(m.getEmployeeId().equals(p.getOwnerId()))
                .createdAt(m.getCreatedAt())
                .build();
    }

    private String displayName(Employee e, Long pk) {
        if (e == null) return "Employee #" + pk;
        String first = e.getFirstName() == null ? "" : e.getFirstName().trim();
        String last = e.getLastName() == null ? "" : e.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank()) return e.getEmployeeName().trim();
        if (e.getEmail() != null && !e.getEmail().isBlank()) return e.getEmail().trim();
        return "Employee #" + pk;
    }

    private void notifyAdded(Project p, Employee emp, ProjectMember saved, Long actorPk) {
        if (actorPk != null && actorPk.equals(emp.getId())) return;
        try {
            notificationEmitter.emit(NotificationRequest.builder()
                    .recipientId(emp.getEmployeeId()).recipientType("EMPLOYEE")
                    .type("PROJECT_MEMBER_ADDED").action("PROJECT_MEMBER_ADDED")
                    .title("Added to project: " + p.getName())
                    .message("You were added to " + p.getName() + " as " + saved.getRole())
                    .projectId(p.getId())
                    .actorId(actorPk)
                    .priority("NORMAL")
                    .deepLink("/pm/04-projects")
                    .build());
        } catch (Exception ex) {
            log.warn("project-member notification failed: {}", ex.getMessage());
        }
    }
}