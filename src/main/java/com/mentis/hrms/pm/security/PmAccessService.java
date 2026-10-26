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

/**
 * PM access rules (STEP 1 + 1b: tiered permissions).
 *
 * TIERS (highest to lowest)
 *   SUPER ADMIN : everything.
 *   ADMINISTER  : Super Admin, project owner, project MANAGER.
 *                 Project edit, dates, baseline, closure, members, rates, finance, archive.
 *   LEAD        : ADMINISTER + explicit project LEAD + lead of the project's team.
 *                 Tasks (create / assign / edit / review), sprints, boards.
 *                 (This is what canManageProject() means now. Existing callers keep working.)
 *   MEMBER      : works on tasks assigned to them.
 *   VIEWER      : read only.
 *
 * HR / HR_MANAGER: people-admin. They can SEE every project (isAdmin = read level) and manage
 * teams / clients / timesheets / leave data, but they do NOT create projects directly and do NOT
 * get edit, finance or approval rights on projects. HR_MANAGER reaches a new project only by
 * approving an intake request.
 *
 * Every role check works for ANY employee id (not only the current web user), so these
 * methods are also safe to call from schedulers and async jobs.
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
    private final EmployeeRepository employeeRepository;

    // ═════════════════════════════════════════════════════════
    // GLOBAL ROLES
    // ═════════════════════════════════════════════════════════

    /** HRMS role of any employee. Uses the request user when it is the same person (no DB hit). */
    private UserRole roleOf(Long userId) {
        if (userId == null) return null;
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();
        if (u != null && Objects.equals(userId, u.employeePk())) return u.hrmsRole();
        return employeeRepository.findById(userId).map(Employee::getRole).orElse(null);
    }

    public boolean isSuperAdmin(Long userId) {
        return roleOf(userId) == UserRole.SUPER_ADMIN;
    }

    /** HR and HR_MANAGER: people-admin, read-only on projects. */
    public boolean isPeopleAdmin(Long userId) {
        UserRole r = roleOf(userId);
        return r == UserRole.HR || r == UserRole.HR_MANAGER;
    }

    /**
     * READ-LEVEL company admin (Super Admin, HR, HR_MANAGER): sees every project and the whole
     * intake queue. It no longer means "may edit". For write rights use isSuperAdmin() or the
     * canAdminister / canManage checks below.
     */
    public boolean isAdmin(Long userId) {
        return isSuperAdmin(userId) || isPeopleAdmin(userId);
    }

    public boolean isPmRole(Long userId) {
        return roleOf(userId) == UserRole.PM;
    }

    /** May approve / reject project requests and define templates. */
    public boolean isIntakeReviewer(Long userId) {
        UserRole r = roleOf(userId);
        return r == UserRole.SUPER_ADMIN || r == UserRole.HR_MANAGER;
    }

    /** Admins (read) and PMs may read the whole intake queue. Only reviewers may decide. */
    public boolean canViewIntakeQueue(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    /**
     * STEP 1b: Only a Super Admin or a user with the PM role may create a project directly
     * (also used for "create from template"). HR / HR_MANAGER no longer can; they go through
     * the intake queue.
     */
    public boolean canCreateProject(Long userId) {
        return isSuperAdmin(userId) || isPmRole(userId);
    }

    public void requireCanCreateProject(Long userId) {
        if (!canCreateProject(userId))
            throw new PmAccessDeniedException("Only a Super Admin or a project manager can create a project directly. Submit a project request instead.");
    }

    /** STEP 1b: teams are people structure. Admins (incl. HR) and PMs may create them. */
    public boolean canCreateTeam(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    public void requireCanCreateTeam(Long userId) {
        if (!canCreateTeam(userId))
            throw new PmAccessDeniedException("Only admins and project managers can create teams.");
    }

    /** STEP 1b: client directory. Admins (incl. HR) and PMs may create and delete clients. */
    public boolean canManageClients(Long userId) {
        return isAdmin(userId) || isPmRole(userId);
    }

    public void requireCanManageClients(Long userId) {
        if (!canManageClients(userId))
            throw new PmAccessDeniedException("Only admins and project managers can manage clients.");
    }

    /** Read-level admin check (kept for existing callers). */
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

    // ═════════════════════════════════════════════════════════
    // BUILDING BLOCKS (all take an explicit employee id)
    // ═════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public boolean isProjectOwner(Long userId, Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> p.getOwnerId() != null && p.getOwnerId().equals(userId))
                .orElse(false);
    }

    /** Explicit MANAGER on the project. */
    @Transactional(readOnly = true)
    public boolean isProjectManager(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> m.getRole() == ProjectRole.MANAGER).orElse(false);
    }

    /** Explicit LEAD role on the project (STEP 1). */
    @Transactional(readOnly = true)
    public boolean isProjectLeadRole(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId)
                .map(m -> m.getRole() == ProjectRole.LEAD).orElse(false);
    }

    /** Any explicit project role. */
    @Transactional(readOnly = true)
    public boolean isProjectMember(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        return projectMemberRepository.findByProjectIdAndEmployeeId(projectId, userId).isPresent();
    }

    /** Explicit VIEWER on the project (read-only, even if assigned a task). */
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

    /** Explicit LEAD role OR lead of the project's team. */
    @Transactional(readOnly = true)
    public boolean isProjectLead(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (isProjectLeadRole(userId, projectId)) return true;
        return projectRepository.findById(projectId)
                .map(Project::getTeamId)
                .map(tid -> isTeamLead(userId, tid))
                .orElse(false);
    }

    /**
     * STEP 1b: ids of every project this person can ADMINISTER without being Super Admin
     * (owner, or explicit MANAGER). One call for a whole list, so list endpoints do not run
     * one query per project row. Super Admin is NOT expanded here: callers check
     * isSuperAdmin() first.
     */
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

    // ═════════════════════════════════════════════════════════
    // PROJECT-LEVEL TIERS
    // ═════════════════════════════════════════════════════════

    /** ADMINISTER tier: Super Admin, owner, project MANAGER. NOT HR, NOT team lead. */
    @Transactional(readOnly = true)
    public boolean canAdministerProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (isSuperAdmin(userId)) return true;
        return isProjectOwner(userId, projectId) || isProjectManager(userId, projectId);
    }

    /** Budget, hourly rates, baseline history. Same people as ADMINISTER. */
    @Transactional(readOnly = true)
    public boolean canViewFinance(Long userId, Long projectId) {
        return canAdministerProject(userId, projectId);
    }

    /**
     * LEAD tier (the name is kept so existing callers do not change):
     * ADMINISTER + explicit LEAD + lead of the project's team.
     * HR is not included here.
     */
    @Transactional(readOnly = true)
    public boolean canManageProject(Long userId, Long projectId) {
        if (canAdministerProject(userId, projectId)) return true;
        return isProjectLead(userId, projectId);
    }

    /** Read access: admins (incl. HR), the lead tier, any project role, team members, task assignees. */
    @Transactional(readOnly = true)
    public boolean canViewProject(Long userId, Long projectId) {
        if (userId == null || projectId == null) return false;
        if (isAdmin(userId)) return true;
        if (canManageProject(userId, projectId)) return true;
        if (isProjectMember(userId, projectId)) return true;
        Long teamId = projectRepository.findById(projectId).map(Project::getTeamId).orElse(null);
        if (isTeamMember(userId, teamId)) return true;
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

    // ═════════════════════════════════════════════════════════
    // TEAM-LEVEL
    // ═════════════════════════════════════════════════════════

    /** Admin (incl. HR: teams are people structure), the team lead, or the person who created the team. */
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

    /** Self, admin (incl. HR), or lead of a team the employee belongs to. (Reworked in step 6.) */
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

    /** Super Admin, owner, MANAGER, LEAD role, team lead, or task assignee (VIEWERs excluded). */
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

    /** Super Admin, owner, MANAGER, LEAD role or team lead can create tasks inside a project. */
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

    /** Admins (incl. HR, read), owner, any project role, team lead/member, assignee, or watcher. */
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
        assertProjectOpen(projectIdOfTask(taskId));
    }

    /** Super Admin, owner, MANAGER, LEAD or team lead may assign / reassign. */
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

    /** Super Admin, owner or project MANAGER may delete. */
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
    }    /** Client directory: admins (incl. HR), PMs, and anyone who owns / manages a project. */
    @Transactional(readOnly = true)
    public boolean canViewClients(Long userId) {
        return canManageClients(userId) || !managedProjectIds(userId).isEmpty();
    }

    public void requireCanViewClients(Long userId) {
        if (!canViewClients(userId))
            throw new PmAccessDeniedException("You don't have permission to see the client directory.");
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
        assertProjectOpen(projectId);
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

    /** Only the author or a Super Admin may delete a comment. */
    @Transactional(readOnly = true)
    public void requireCanDeleteComment(Long userId, Long commentId) {
        TaskComment c = taskCommentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
        if (isSuperAdmin(userId)) return;
        if (c.getAuthorId() != null && c.getAuthorId().equals(userId)) return;
        throw new PmAccessDeniedException("Only the author or a Super Admin can delete this comment.");
    }
    // ═════════════════════════════════════════════════════════
    // STEP 4: REVIEW RULE
    // ═════════════════════════════════════════════════════════

    /** Does this project require tasks to go through In Review? */
    @Transactional(readOnly = true)
    public boolean isReviewRequired(Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> Boolean.TRUE.equals(p.getReviewRequired()))
                .orElse(false);
    }

    /** Reviewer = same people who may assign: Super Admin, owner, MANAGER, LEAD role, team lead. */
    @Transactional(readOnly = true)
    public boolean canReviewTask(Long userId, Long taskId) {
        return canAssignTask(userId, taskId);
    }

    /**
     * True when this person may NOT skip review on this task:
     * the project requires review and they are not a reviewer. Safe for any employee id.
     */
    @Transactional(readOnly = true)
    public boolean isReviewGateActive(Long userId, Long taskId) {
        Long projectId = projectIdOfTask(taskId);
        return isReviewRequired(projectId) && !canReviewTask(userId, taskId);
    }

    // ═════════════════════════════════════════════════════════
    // STEP 6: people-side data (hours) and team visibility
    // ═════════════════════════════════════════════════════════

    /**
     * Who may see the hours people logged on ONE project: lead tier or higher (owner, MANAGER,
     * LEAD, team lead, Super Admin) and the people-admins (HR / HR_MANAGER, read-only).
     * Note: after Step 1, HR is no longer "lead tier", so without this the HR timesheet and
     * workload views of a project would return 403.
     */
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

    /** Team data (members, team task list): admins (incl. HR), PMs, the team lead and team members. */
    @Transactional(readOnly = true)
    public boolean canViewTeam(Long userId, Long teamId) {
        if (userId == null || teamId == null) return false;
        if (isAdmin(userId) || isPmRole(userId)) return true;
        return isTeamLead(userId, teamId) || isTeamMember(userId, teamId);
    }
    // ═════════════════════════════════════════════════════════
    // STEP 8: completed / archived projects are locked
    // ═════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public boolean isProjectLocked(Long projectId) {
        if (projectId == null) return false;
        return projectRepository.findById(projectId)
                .map(p -> p.getStatus() == ProjectStatus.COMPLETED || p.getStatus() == ProjectStatus.ARCHIVED)
                .orElse(false);
    }

    /** Called AFTER the permission check, so people without permission still see the permission message. */
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
    // ═════════════════════════════════════════════════════════
    // APPROVALS: who may be named as approver, and who may decide
    // ═════════════════════════════════════════════════════════

    /**
     * Same rule is used when a request is created (the chosen approver must qualify) and when
     * it is decided. Works for any employee id.
     *   BUDGET / CHANGE_REQUEST / CLIENT_SIGN_OFF : Super Admin only
     *   PROJECT (status change)                   : Super Admin, owner or MANAGER
     *   TASK (sign-off)                           : lead tier or higher
     *   anything else (TIMESHEET ...)             : routed by its own service
     */
    @Transactional(readOnly = true)
    public boolean isEligibleApprover(String entityType, Long entityId, Long approverId) {
        if (entityType == null || approverId == null) return false;
        return switch (entityType.toUpperCase()) {
            case "BUDGET", "CHANGE_REQUEST", "CLIENT_SIGN_OFF" -> isSuperAdmin(approverId);
            case "PROJECT" -> canAdministerProject(approverId, entityId);
            case "TASK" -> {
                Long pid = projectIdOfTask(entityId);
                yield pid != null && canManageProject(approverId, pid);
            }
            default -> true;
        };
    }
}