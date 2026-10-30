package com.mentis.hrms.pm.security;

import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** STEP F7: server-side rules for who may open a PM page. Same rules as pm-core.js ROUTES. */
@Component
@RequiredArgsConstructor
public class PmPageGuard {

    private final PmAccessService access;
    private final TeamRepository teamRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final EmployeeRepository employeeRepository;

    /** Same rule as PmMeResponse.leadTier: Super Admin, PM, team lead, LEAD role, or owner/MANAGER of a project. */
    public boolean isLeadTier(Long userId) {
        if (userId == null) return false;
        return access.isSuperAdmin(userId)
                || access.isPmRole(userId)
                || !teamRepository.findByLeadEmployeeId(userId).isEmpty()
                || projectMemberRepository.existsByEmployeeIdAndRole(userId, ProjectRole.LEAD)
                || !access.managedProjectIds(userId).isEmpty();
    }

    /** True when this person leads at least one team (Team.leadEmployeeId). */
    public boolean isTeamLead(Long userId) {
        return userId != null && !teamRepository.findByLeadEmployeeId(userId).isEmpty();
    }

    /**
     * True when this person is ONLY a team lead:
     * Leads at least one team, but is not Super Admin, HR Admin, PM, Project Owner/Manager, or Project LEAD role.
     */
    public boolean isTeamLeadOnly(Long userId) {
        if (userId == null) return false;
        return isTeamLead(userId)
                && !access.isSuperAdmin(userId)
                && !access.isAdmin(userId)
                && !access.isPmRole(userId)
                && access.managedProjectIds(userId).isEmpty()
                && !projectMemberRepository.existsByEmployeeIdAndRole(userId, ProjectRole.LEAD);
    }

    /** Same checks, but starting from the HRMS employee code kept in the session ("userId" attribute). */
    public boolean canOpenPmByCode(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) return false;
        return employeeRepository.findByEmployeeId(employeeCode)
                .map(e -> isLeadTier(e.getId())).orElse(false);
    }

    public boolean isTeamLeadByCode(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) return false;
        return employeeRepository.findByEmployeeId(employeeCode)
                .map(e -> isTeamLead(e.getId())).orElse(false);
    }

    public boolean isTeamLeadOnlyByCode(String employeeCode) {
        if (employeeCode == null || employeeCode.isBlank()) return false;
        return employeeRepository.findByEmployeeId(employeeCode)
                .map(e -> isTeamLeadOnly(e.getId())).orElse(false);
    }

    /** Client Directory: Super Admin, HR, HR Manager, PM. */
    public boolean canOpenClients(Long userId) {
        return userId != null && access.canManageClients(userId);
    }

    /** Insights (reports): admins and the lead tier. The Budget tab has its own finance rule on the API. */
    public boolean canOpenInsights(Long userId) {
        return userId != null && (access.isAdmin(userId) || isLeadTier(userId));
    }
}