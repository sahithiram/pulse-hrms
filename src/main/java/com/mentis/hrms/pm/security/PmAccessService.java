package com.mentis.hrms.pm.security;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskChecklistItem;
import com.mentis.hrms.pm.entity.TaskComment;
import com.mentis.hrms.pm.entity.TaskDependency;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskAssignmentRepository;
import com.mentis.hrms.pm.repository.TaskChecklistItemRepository;
import com.mentis.hrms.pm.repository.TaskCommentRepository;
import com.mentis.hrms.pm.repository.TaskDependencyRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TaskWatcherRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import com.mentis.hrms.pm.repository.TimesheetWeekRepository;

@Service
@RequiredArgsConstructor
public class PmAccessService {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskRepository taskRepository;
    private final TaskWatcherRepository taskWatcherRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskChecklistItemRepository taskChecklistItemRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final TimesheetWeekRepository timesheetWeekRepository;

    private UserRole roleOf(Long userId) {
        if (userId == null) return null;
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();
        if (u != null && Objects.equals(userId, u.employeePk())) return u.hrmsRole();
        return employeeRepository.findById(userId).map(Employee::getRole).orElse(null);
    }

    public boolean isSuperAdmin(Long userId) {
        return roleOf(userId) == UserRole.SUPER_ADMIN;
    }

    public boolean isPeopleAdmin(Long userId) {
        UserRole r = roleOf(userId);
        return r == UserRole.HR || r == UserRole.HR_MANAGER;
    }

    public boolean isAdmin(Long userId) {
        return isSuperAdmin(userId) || isPeopleAdmin(userId);
    }

    public boolean isPmRole(Long userId) {
        return roleOf(userId) == UserRole.PM;
    }

    public boolean isIntakeReviewer(Long userId) {
        UserRole r = roleOf(userId);
        return r == UserRole.SUPER_ADMIN || r == UserRole.HR_MANAGER;
    }

    public boolean canViewIntakeQueue(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    public boolean canCreateProject(Long userId) {
        return isSuperAdmin(userId) || isPmRole(userId);
    }

    public void requireCanCreateProject(Long userId) {
        if (!canCreateProject(userId))
            throw new PmAccessDeniedException("Only a Super Admin or a project manager can create a project directly. Submit a project request instead.");
    }

    public boolean isHr(Long userId) {
        return isPeopleAdmin(userId);
    }

    public boolean canManageTeamLeads(Long userId) {
        return isAdmin(userId);
    }

    @Transactional(readOnly = true)
    public boolean isAnyProjectManager(Long userId) {
        return userId != null && !managedProjectIds(userId).isEmpty();
    }

    public boolean canCreateTeam(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    public void requireCanCreateTeam(Long userId) {
        if (!canCreateTeam(userId))
            throw new PmAccessDeniedException("Only a Super Admin or the team's creator can do this.");
    }

    public boolean canManageClients(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    public void requireCanManageClients(Long userId) {
        if (!canManageClients(userId))
            throw new PmAccessDeniedException("Only admins and project managers can manage clients.");
    }

    public void requireAdmin(Long userId) {
        if (!isAdmin(userId))
            throw new PmAccessDeniedException("Admins only.");
    }

    public void requireSuperAdmin(Long userId) {
        if (!isSuperAdmin(userId))
            throw new PmAccessDeniedException("Super Admin only.");
    }

    public void requireIntakeReviewer(Long userId) {
        if (!isIntakeReviewer(userId))
            throw new PmAccessDeniedException("Only a Super Admin or HR Manager can do this.");
    }

    @Transactional(readOnly = true)
    public boolean isProjectOwner(Long userId, Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> p.getOwnerId() != null && p.getOwnerId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isProjectManager(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> m.getRole() == ProjectRole.MANAGER).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isProjectLeadRole(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> m.getRole() == ProjectRole.LEAD).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isProjectViewerOnly(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> m.getRole() == ProjectRole.VIEWER).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isTeamLead(Long userId, Long teamId) {
        if (teamId == null) return false;
        return teamRepository.findById(teamId)
                .map(t -> t.getLeadEmployeeId() != null && t.getLeadEmployeeId().equals(userId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isTeamMember(Long userId, Long teamId) {
        if (teamId == null) return false;
        return teamMemberRepository.findByTeamId(teamId).stream()
                .anyMatch(tm -> tm.getEmployeeId().equals(userId));
    }

    @Transactional(readOnly = true)
    public boolean isTaskAssignee(Long userId, Long taskId) {
        if (taskId == null) return false;
        return taskAssignmentRepository.findByTaskId(taskId).stream()
                .anyMatch(a -> a.getEmployeeId().equals(userId));
    }

    @Transactional(readOnly = true)
    public boolean isProjectLead(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (isProjectLeadRole(userId, projectId)) return true;
        return projectRepository.findById(projectId)
                .map(Project::getTeamId)
                .map(tid -> isTeamLead(userId, tid))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Set<Long> managedProjectIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        if (userId == null) return ids;
        projectRepository.findByOwnerId(userId).forEach(p -> ids.add(p.getId()));
        projectMemberRepository.findByEmployeeId(userId).stream()
                .filter(m -> m.getRole() == ProjectRole.MANAGER)
                .forEach(m -> ids.add(m.getProjectId()));
        return ids;
    }

    @Transactional(readOnly = true)
    public boolean canAdministerProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (isSuperAdmin(userId)) return true;
        return isProjectOwner(userId, projectId) || isProjectManager(userId, projectId);
    }

    @Transactional(readOnly = true)
    public boolean canViewFinance(Long userId, Long projectId) {
        return canAdministerProject(userId, projectId);
    }

    @Transactional(readOnly = true)
    public boolean canManageProject(Long userId, Long projectId) {
        if (canAdministerProject(userId, projectId)) return true;
        return isProjectLead(userId, projectId);
    }

    @Transactional(readOnly = true)
    public boolean canViewProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (isAdmin(userId)) return true;
        if (canManageProject(userId, projectId)) return true;
        if (isProjectMember(userId, projectId)) return true;
        Long teamId = projectRepository.findById(projectId).map(Project::getTeamId).orElse(null);
        if (isTeamMember(userId, teamId) || isTeamLead(userId, teamId)) return true;
        return taskAssignmentRepository.existsByProjectAndEmployee(projectId, userId);
    }

    @Transactional(readOnly = true)
    public void requireCanAdminister(Long userId, Long projectId) {
        if (!canAdministerProject(userId, projectId))
            throw new PmAccessDeniedException("Only the project owner, a project manager or a Super Admin can do this.");
    }

    @Transactional(readOnly = true)
    public void requireCanViewFinance(Long userId, Long projectId) {
        if (!canViewFinance(userId, projectId))
            throw new PmAccessDeniedException("You don't have permission to see budget and rates for this project.");
    }

    @Transactional(readOnly = true)
    public void requireCanManageProject(Long userId, Long projectId) {
        if (!canManageProject(userId, projectId))
            throw new PmAccessDeniedException("You don't have permission to manage this project.");
    }

    @Transactional(readOnly = true)
    public void requireCanViewProject(Long userId, Long projectId) {
        if (!canViewProject(userId, projectId))
            throw new PmAccessDeniedException("You don't have permission to view this project.");
    }

    @Transactional(readOnly = true)
    public boolean canManageTeam(Long userId, Long teamId) {
        if (isSuperAdmin(userId)) return true;
        if (userId == null || teamId == null) return false;
        return teamRepository.findById(teamId)
                .map(t -> userId.equals(t.getCreatedBy()))
                .orElse(false);
    }

    public void requireCanManageTeam(Long userId, Long teamId) {
        if (!canManageTeam(userId, teamId))
            throw new PmAccessDeniedException("Only admins, the team lead or the team's creator can do this.");
    }

    @Transactional(readOnly = true)
    public boolean canViewEmployeeTimesheet(Long viewerId, Long employeePk) {
        if (viewerId == null || employeePk == null) return false;
        if (viewerId.equals(employeePk)) return true;
        if (isAdmin(viewerId)) return true;
        if (teamMemberRepository.findByEmployeeId(employeePk).stream()
                .anyMatch(m -> isTeamLead(viewerId, m.getTeamId()))) return true;
        return timesheetWeekRepository.existsByEmployeeIdAndApproverId(employeePk, viewerId);
    }

    @Transactional(readOnly = true)
    public boolean canEditTask(Long userId, Long taskId, Long projectId, Long teamId) {
        if (isSuperAdmin(userId)) return true;
        if (isProjectOwner(userId, projectId)) return true;
        if (isProjectManager(userId, projectId)) return true;
        if (isProjectLeadRole(userId, projectId)) return true;
        if (isTeamLead(userId, teamId)) return true;
        if (isProjectViewerOnly(userId, projectId)) return false;
        return isTaskAssignee(userId, taskId);
    }

    @Transactional(readOnly = true)
    public boolean canCreateTask(Long userId, Long projectId, Long teamId) {
        if (isSuperAdmin(userId)) return true;
        if (isProjectOwner(userId, projectId)) return true;
        if (isProjectManager(userId, projectId)) return true;
        if (isProjectLeadRole(userId, projectId)) return true;
        return isTeamLead(userId, teamId);
    }

    @Transactional(readOnly = true)
    public Long projectIdOfTask(Long taskId) {
        if (taskId == null) return null;
        return taskRepository.findById(taskId).map(Task::getProjectId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Long teamIdOfTask(Long taskId) {
        if (taskId == null) return null;
        return taskRepository.findById(taskId).map(Task::getTeamId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean canViewTask(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        if (isAdmin(userId)) return true;

        Long projectId = projectIdOfTask(taskId);
        Long teamId = teamIdOfTask(taskId);

        if (isProjectOwner(userId, projectId)) return true;
        if (isProjectMember(userId, projectId)) return true;
        if (isTeamLead(userId, teamId)) return true;
        if (isTeamMember(userId, teamId)) return true;
        if (isTaskAssignee(userId, taskId)) return true;

        return taskWatcherRepository.findByTaskId(taskId).stream()
                .anyMatch(w -> w.getEmployeeId().equals(userId));
    }

    @Transactional(readOnly = true)
    public boolean canEditTaskAccess(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        return canEditTask(userId, taskId, projectIdOfTask(taskId), teamIdOfTask(taskId));
    }

    @Transactional(readOnly = true)
    public void requireCanView(Long userId, Long taskId) {
        if (!canViewTask(userId, taskId))
            throw new PmAccessDeniedException("You don't have permission to view this task.");
    }

    @Transactional(readOnly = true)
    public void requireCanEdit(Long userId, Long taskId) {
        if (!canEditTaskAccess(userId, taskId))
            throw new PmAccessDeniedException("You don't have permission to modify this task.");
        assertProjectOpen(projectIdOfTask(taskId));
    }

    @Transactional(readOnly = true)
    public boolean canAssignTask(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        if (isSuperAdmin(userId)) return true;
        Long projectId = projectIdOfTask(taskId);
        return isProjectOwner(userId, projectId)
                || isProjectManager(userId, projectId)
                || isProjectLeadRole(userId, projectId)
                || isTeamLead(userId, teamIdOfTask(taskId));
    }

    @Transactional(readOnly = true)
    public boolean canDeleteTask(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        if (isSuperAdmin(userId)) return true;
        Long projectId = projectIdOfTask(taskId);
        return isProjectOwner(userId, projectId) || isProjectManager(userId, projectId);
    }

    public void requireCanAssign(Long userId, Long taskId) {
        if (!canAssignTask(userId, taskId))
            throw new PmAccessDeniedException("You don't have permission to assign this task.");
        assertProjectOpen(projectIdOfTask(taskId));
    }

    public void requireCanDelete(Long userId, Long taskId) {
        if (!canDeleteTask(userId, taskId))
            throw new PmAccessDeniedException("You don't have permission to delete this task.");
        assertProjectOpen(projectIdOfTask(taskId));
    }

    @Transactional(readOnly = true)
    public boolean canViewClients(Long userId) {
        return canManageClients(userId) || !managedProjectIds(userId).isEmpty();
    }

    public void requireCanViewClients(Long userId) {
        if (!canViewClients(userId))
            throw new PmAccessDeniedException("You don't have permission to see the client directory.");
    }

    @Transactional(readOnly = true)
    public void requireCanCreate(Long userId, Long projectId, Long teamId) {
        Long effectiveTeamId = teamId;
        if (effectiveTeamId == null && projectId != null) {
            effectiveTeamId = projectRepository.findById(projectId)
                    .map(Project::getTeamId).orElse(null);
        }
        if (teamId != null && projectId != null) {
            Long projectTeamId = projectRepository.findById(projectId)
                    .map(Project::getTeamId).orElse(null);
            if (projectTeamId != null && !projectTeamId.equals(teamId)
                    && !isSuperAdmin(userId)
                    && !isProjectOwner(userId, projectId)
                    && !isProjectManager(userId, projectId)
                    && !isProjectLeadRole(userId, projectId)) {
                throw new PmAccessDeniedException("You can't create tasks for another team's project.");
            }
        }
        if (!canCreateTask(userId, projectId, effectiveTeamId))
            throw new PmAccessDeniedException("You can't create tasks in this project.");
        assertProjectOpen(projectId);
    }

    @Transactional(readOnly = true)
    public void requireAssigneesAllowed(Long userId, Long projectId, Long teamId, java.util.Collection<Long> assigneeIds) {
        if (assigneeIds == null || assigneeIds.isEmpty()) return;
        if (isSuperAdmin(userId) || isProjectOwner(userId, projectId)
                || isProjectManager(userId, projectId) || isProjectLeadRole(userId, projectId)) return;
        if (teamId == null) throw new PmAccessDeniedException("A team is required to assign people.");
        Set<Long> ok = new HashSet<>();
        teamMemberRepository.findByTeamId(teamId).forEach(tm -> ok.add(tm.getEmployeeId()));
        teamRepository.findById(teamId).map(t -> t.getLeadEmployeeId()).ifPresent(ok::add);
        if (!ok.containsAll(assigneeIds))
            throw new PmAccessDeniedException("You can only assign people who are in your team.");
    }

    @Transactional(readOnly = true)
    public void requireCanEditDependency(Long userId, Long depId) {
        TaskDependency d = taskDependencyRepository.findById(depId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency not found: " + depId));
        requireCanEdit(userId, d.getTaskId());
    }

    @Transactional(readOnly = true)
    public void requireCanEditChecklistItem(Long userId, Long itemId) {
        TaskChecklistItem item = taskChecklistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + itemId));
        requireCanEdit(userId, item.getTaskId());
    }

    @Transactional(readOnly = true)
    public void requireCanDeleteComment(Long userId, Long commentId) {
        TaskComment c = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (isSuperAdmin(userId)) return;
        if (c.getAuthorId() != null && c.getAuthorId().equals(userId)) return;
        throw new PmAccessDeniedException("Only the author or a Super Admin can delete this comment.");
    }

    @Transactional(readOnly = true)
    public boolean isReviewRequired(Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> Boolean.TRUE.equals(p.getReviewRequired()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canReviewTask(Long userId, Long taskId) {
        return canAssignTask(userId, taskId);
    }

    @Transactional(readOnly = true)
    public boolean isReviewGateActive(Long userId, Long taskId) {
        Long projectId = projectIdOfTask(taskId);
        return isReviewRequired(projectId) && !canReviewTask(userId, taskId);
    }

    @Transactional(readOnly = true)
    public boolean canViewProjectPeople(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return isAdmin(userId) || canManageProject(userId, projectId);
    }

    @Transactional(readOnly = true)
    public void requireCanViewProjectPeople(Long userId, Long projectId) {
        if (!canViewProjectPeople(userId, projectId))
            throw new PmAccessDeniedException("You don't have permission to see this project's team hours.");
    }

    @Transactional(readOnly = true)
    public boolean canViewTeam(Long userId, Long teamId) {
        if (userId == null || teamId == null) return false;
        if (isAdmin(userId) || isPmRole(userId)) return true;
        return isTeamLead(userId, teamId) || isTeamMember(userId, teamId);
    }

    @Transactional(readOnly = true)
    public boolean isProjectLocked(Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> p.getStatus() == ProjectStatus.COMPLETED || p.getStatus() == ProjectStatus.ARCHIVED)
                .orElse(false);
    }

    private void assertProjectOpen(Long projectId) {
        if (isProjectLocked(projectId))
            throw new BusinessRuleException(
                    "This project is completed or archived, so its tasks are locked. A Super Admin can reopen it.");
    }

    @Transactional(readOnly = true)
    public void requireCanViewTeam(Long userId, Long teamId) {
        if (!canViewTeam(userId, teamId))
            throw new PmAccessDeniedException("You don't have permission to see this team.");
    }

    public boolean canSeeAllTeams(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    @Transactional(readOnly = true)
    public Set<Long> myTeamIds(Long userId) {
        Set<Long> ids = new HashSet<>();
        if (userId == null) return ids;

        teamRepository.findByLeadEmployeeId(userId).forEach(t -> ids.add(t.getId()));
        teamMemberRepository.findByEmployeeId(userId).forEach(m -> ids.add(m.getTeamId()));

        Set<Long> projectIds = new HashSet<>(managedProjectIds(userId));
        projectMemberRepository.findByEmployeeId(userId)
                .forEach(m -> projectIds.add(m.getProjectId()));
        if (!projectIds.isEmpty()) {
            projectRepository.findAllById(projectIds).forEach(p -> {
                if (p.getTeamId() != null) ids.add(p.getTeamId());
            });
        }
        return ids;
    }

    public boolean canDeleteClient(Long userId) {
        return isSuperAdmin(userId) || isPmRole(userId);
    }

    public void requireCanDeleteClient(Long userId) {
        if (!canDeleteClient(userId))
            throw new PmAccessDeniedException("Only a Super Admin or a project manager can delete a client.");
    }

    @Transactional(readOnly = true)
    public boolean canManageTeamMembers(Long userId, Long teamId) {
        if (userId == null || teamId == null) return false;
        return canManageTeam(userId, teamId) || isTeamLead(userId, teamId);
    }

    public void requireCanManageTeamMembers(Long userId, Long teamId) {
        if (!canManageTeamMembers(userId, teamId))
            throw new PmAccessDeniedException(
                    "Only a Super Admin, the team's creator or the team lead can change its members.");
    }

    @Transactional(readOnly = true)
    public Set<Long> pmPortfolioProjectIds(Long userId) {
        Set<Long> ids = managedProjectIds(userId);
        projectMemberRepository.findByEmployeeId(userId)
                .forEach(m -> ids.add(m.getProjectId()));
        return ids;
    }

    @Transactional(readOnly = true)
    public boolean isEligibleApprover(String entityType, Long entityId, Long approverId) {
        if (entityType == null || approverId == null) return false;
        return switch (entityType.toUpperCase()) {
            case "BUDGET", "CHANGE_REQUEST" -> isSuperAdmin(approverId);
            case "CLIENT_SIGN_OFF", "PROJECT" -> canAdministerProject(approverId, entityId);
            case "TASK", "EXTENSION" -> {
                Long pid = projectIdOfTask(entityId);
                yield pid != null && canManageProject(approverId, pid);
            }
            default -> true;
        };
    }
}
