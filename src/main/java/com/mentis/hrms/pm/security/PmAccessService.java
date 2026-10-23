package com.mentis.hrms.pm.security;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PM access rules.
 * NOTE: isAdmin()/canCreateProject() read the CURRENT web request user, so they
 * are only valid inside a web request, never from a scheduler or async job.
 */
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

    // ═════════════════════════════════════════════════════════
    // GLOBAL ROLES
    // ═════════════════════════════════════════════════════════

    /** Company-level admin. PHASE 2: the PM role is NOT an admin any more. */
    public boolean isAdmin(Long userId) {
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();
        if (u == null || u.hrmsRole() == null) return false;
        return u.hrmsRole() == UserRole.SUPER_ADMIN
                || u.hrmsRole() == UserRole.HR
                || u.hrmsRole() == UserRole.HR_MANAGER;
    }

    /** Admins and users with the PM role may create projects / teams / clients. */
    public boolean canCreateProject(Long userId) {
        if (isAdmin(userId)) return true;
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();
        return u != null && u.hrmsRole() == UserRole.PM;
    }

    public void requireCanCreateProject(Long userId) {
        if (!canCreateProject(userId))
            throw new PmAccessDeniedException("Only admins and project managers can do this.");
    }

    public void requireAdmin(Long userId) {
        if (!isAdmin(userId))
            throw new PmAccessDeniedException("Admins only.");
    }

    // ═════════════════════════════════════════════════════════
    // BUILDING BLOCKS
    // ═════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public boolean isProjectOwner(Long userId, Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> p.getOwnerId() != null && p.getOwnerId().equals(userId))
                .orElse(false);
    }

    /** PHASE 2: explicit MANAGER on the project. */
    @Transactional(readOnly = true)
    public boolean isProjectManager(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> m.getRole() == ProjectRole.MANAGER).orElse(false);
    }

    /** PHASE 2: any explicit project role. */
    @Transactional(readOnly = true)
    public boolean isProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId).isPresent();
    }

    /** PHASE 2: explicit VIEWER on the project (read-only, even if assigned a task). */
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

    // ═════════════════════════════════════════════════════════
    // PROJECT-LEVEL
    // ═════════════════════════════════════════════════════════

    /** Admin, owner, project MANAGER, or lead of the project's team. */
    @Transactional(readOnly = true)
    public boolean canManageProject(Long userId, Long projectId) {
        if (isAdmin(userId)) return true;
        if (isProjectOwner(userId, projectId)) return true;
        if (isProjectManager(userId, projectId)) return true;
        return projectRepository.findById(projectId)
                .map(Project::getTeamId)
                .map(tid -> isTeamLead(userId, tid))
                .orElse(false);
    }

    /** Manage rights, any project role, team member, or assignee of a task in the project. */
    @Transactional(readOnly = true)
    public boolean canViewProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (canManageProject(userId, projectId)) return true;
        if (isProjectMember(userId, projectId)) return true;
        Long teamId = projectRepository.findById(projectId).map(Project::getTeamId).orElse(null);
        if (isTeamMember(userId, teamId)) return true;
        return taskAssignmentRepository.existsByProjectAndEmployee(projectId, userId);
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

    // ═════════════════════════════════════════════════════════
    // TEAM-LEVEL (PHASE 2)
    // ═════════════════════════════════════════════════════════

    /** Admin, the team lead, or the person who created the team. */
    @Transactional(readOnly = true)
    public boolean canManageTeam(Long userId, Long teamId) {
        if (isAdmin(userId)) return true;
        if (userId == null || teamId == null) return false;
        return teamRepository.findById(teamId)
                .map(t -> userId.equals(t.getLeadEmployeeId()) || userId.equals(t.getCreatedBy()))
                .orElse(false);
    }

    public void requireCanManageTeam(Long userId, Long teamId) {
        if (!canManageTeam(userId, teamId))
            throw new PmAccessDeniedException("Only admins, the team lead or the team's creator can do this.");
    }

    /** Self, admin, or lead of a team the employee belongs to. */
    @Transactional(readOnly = true)
    public boolean canViewEmployeeTimesheet(Long viewerId, Long employeePk) {
        if (viewerId == null || employeePk == null) return false;
        if (viewerId.equals(employeePk)) return true;
        if (isAdmin(viewerId)) return true;
        return teamMemberRepository.findByEmployeeId(employeePk).stream()
                .anyMatch(m -> isTeamLead(viewerId, m.getTeamId()));
    }

    // ═════════════════════════════════════════════════════════
    // TASK-LEVEL
    // ═════════════════════════════════════════════════════════

    /** Admin, owner, project MANAGER, team lead, or task assignee (VIEWERs excluded). */
    @Transactional(readOnly = true)
    public boolean canEditTask(Long userId, Long taskId, Long projectId, Long teamId) {
        if (isAdmin(userId)) return true;
        if (isProjectOwner(userId, projectId)) return true;
        if (isProjectManager(userId, projectId)) return true;
        if (isTeamLead(userId, teamId)) return true;
        if (isProjectViewerOnly(userId, projectId)) return false;
        return isTaskAssignee(userId, taskId);
    }

    /** Admin, owner, project MANAGER or team lead can create tasks inside a project. */
    @Transactional(readOnly = true)
    public boolean canCreateTask(Long userId, Long projectId, Long teamId) {
        if (isAdmin(userId)) return true;
        if (isProjectOwner(userId, projectId)) return true;
        if (isProjectManager(userId, projectId)) return true;
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

    /** Admin, owner, manager, any project role, team lead/member, assignee, or watcher. */
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

    /** Modify a task (status, comment, log time...). Same rule as canEditTask. */
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
    }

    /** Admin, owner, project MANAGER or team lead may assign / reassign. */
    @Transactional(readOnly = true)
    public boolean canAssignTask(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        if (isAdmin(userId)) return true;
        Long projectId = projectIdOfTask(taskId);
        return isProjectOwner(userId, projectId)
                || isProjectManager(userId, projectId)
                || isTeamLead(userId, teamIdOfTask(taskId));
    }

    /** Admin, owner or project MANAGER may delete. */
    @Transactional(readOnly = true)
    public boolean canDeleteTask(Long userId, Long taskId) {
        if (userId == null || taskId == null) return false;
        if (isAdmin(userId)) return true;
        Long projectId = projectIdOfTask(taskId);
        return isProjectOwner(userId, projectId) || isProjectManager(userId, projectId);
    }

    public void requireCanAssign(Long userId, Long taskId) {
        if (!canAssignTask(userId, taskId))
            throw new PmAccessDeniedException("You don't have permission to assign this task.");
    }

    public void requireCanDelete(Long userId, Long taskId) {
        if (!canDeleteTask(userId, taskId))
            throw new PmAccessDeniedException("You don't have permission to delete this task.");
    }

    /** Create check that also resolves the project's team when teamId is not sent. */
    @Transactional(readOnly = true)
    public void requireCanCreate(Long userId, Long projectId, Long teamId) {
        Long effectiveTeamId = teamId;
        if (effectiveTeamId == null && projectId != null) {
            effectiveTeamId = projectRepository.findById(projectId)
                    .map(Project::getTeamId).orElse(null);
        }
        if (!canCreateTask(userId, projectId, effectiveTeamId))
            throw new PmAccessDeniedException("You can't create tasks in this project.");
    }

    /** For endpoints that only receive a dependency id. */
    @Transactional(readOnly = true)
    public void requireCanEditDependency(Long userId, Long depId) {
        TaskDependency d = taskDependencyRepository.findById(depId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency not found: " + depId));
        requireCanEdit(userId, d.getTaskId());
    }

    /** For endpoints that only receive a checklist item id. */
    @Transactional(readOnly = true)
    public void requireCanEditChecklistItem(Long userId, Long itemId) {
        TaskChecklistItem item = taskChecklistItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Checklist item not found: " + itemId));
        requireCanEdit(userId, item.getTaskId());
    }

    /** Only the author or an admin may delete a comment. */
    @Transactional(readOnly = true)
    public void requireCanDeleteComment(Long userId, Long commentId) {
        TaskComment c = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (isAdmin(userId)) return;
        if (c.getAuthorId() != null && c.getAuthorId().equals(userId)) return;
        throw new PmAccessDeniedException("Only the author or an admin can delete this comment.");
    }
}