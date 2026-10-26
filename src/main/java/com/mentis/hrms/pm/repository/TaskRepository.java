package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByProjectId(Long projectId);

    List<Task> findByParentTaskId(Long parentTaskId);

    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    long countByStatusNot(TaskStatus status);

    long countByStatus(TaskStatus status);

    List<Task> findByTeamId(Long teamId);
    List<Task> findByProjectIdAndParentTaskIdIsNull(Long projectId);

    long countByTeamIdAndStatusNot(Long teamId, TaskStatus status);

    // ─── PHASE 5 ─────────────────────────────────────────────
    List<Task> findBySprintId(Long sprintId);
    List<Task> findBySprintIdAndStatus(Long sprintId, TaskStatus status);
    long countBySprintId(Long sprintId);
    long countBySprintIdAndStatus(Long sprintId, TaskStatus status);

    List<Task> findByMilestoneId(Long milestoneId);
    long countByMilestoneId(Long milestoneId);
    long countByMilestoneIdAndStatus(Long milestoneId, TaskStatus status);

    /** Backlog = tasks with no sprint, top-level only. */
    @Query("SELECT t FROM Task t WHERE t.sprintId IS NULL " +
            "AND t.parentTaskId IS NULL " +
            "AND (:projectId IS NULL OR t.projectId = :projectId) " +
            "ORDER BY t.createdAt DESC")
    List<Task> findBacklog(@Param("projectId") Long projectId);

    // ── Subtasks ──────────────────────────────────────
    @Query("SELECT t FROM Task t WHERE t.parentTaskId = :parentId ORDER BY t.createdAt ASC")
    List<Task> findSubtasks(@Param("parentId") Long parentId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.parentTaskId = :parentId")
    long countSubtasks(@Param("parentId") Long parentId);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.parentTaskId = :parentId AND t.status = :status")
    long countSubtasksByStatus(@Param("parentId") Long parentId, @Param("status") TaskStatus status);

    // ── Dependencies ──
    @Query("SELECT t FROM Task t " +
            "JOIN TaskDependency d ON d.dependsOnTaskId = t.id " +
            "WHERE d.taskId = :taskId")
    List<Task> findBlockers(@Param("taskId") Long taskId);

    @Query("SELECT t FROM Task t " +
            "JOIN TaskDependency d ON d.taskId = t.id " +
            "WHERE d.dependsOnTaskId = :taskId")
    List<Task> findBlocked(@Param("taskId") Long taskId);

    @Query("SELECT COUNT(t) FROM Task t " +
            "JOIN TaskDependency d ON d.dependsOnTaskId = t.id " +
            "WHERE d.taskId = :taskId " +
            "AND t.status <> :doneStatus")
    long countOpenBlockers(@Param("taskId") Long taskId,
                           @Param("doneStatus") TaskStatus doneStatus);

    @Query("SELECT d FROM TaskDependency d " +
            "WHERE d.taskId = :taskId OR d.dependsOnTaskId = :taskId")
    List<com.mentis.hrms.pm.entity.TaskDependency> findAllInvolving(@Param("taskId") Long taskId);

    // ── Search ──
    @Query("SELECT t FROM Task t WHERE " +
            "t.parentTaskId IS NULL " +
            "AND (:projectId IS NULL OR t.projectId = :projectId) " +
            "AND (:teamId IS NULL OR t.teamId = :teamId) " +
            "AND (:status IS NULL OR t.status = :status) " +
            "AND (:search IS NULL OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Task> searchFull(@Param("projectId") Long projectId,
                          @Param("teamId") Long teamId,
                          @Param("status") TaskStatus status,
                          @Param("search") String search,
                          Pageable pageable);

    default Page<Task> search(Long projectId, TaskStatus status, String search, Pageable pageable) {
        return searchFull(projectId, null, status, search, pageable);
    }

    default Page<Task> search(Long projectId, Long teamId, TaskStatus status, String search, Pageable pageable) {
        return searchFull(projectId, teamId, status, search, pageable);
    }

    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> countGroupByStatus();

    @Query("SELECT t.status, COUNT(t) FROM Task t WHERE t.projectId = :projectId GROUP BY t.status")
    List<Object[]> countGroupByStatusForProject(@Param("projectId") Long projectId);

    @Query("SELECT DISTINCT t FROM Task t WHERE t.id IN (" +
            "  SELECT a.taskId FROM TaskAssignment a WHERE a.employeeId IN :employeeIds" +
            ")")
    List<Task> findAllByAssigneeIn(@Param("employeeIds") Collection<Long> employeeIds);
    @Query("SELECT COUNT(t) FROM Task t WHERE t.projectId IN :ids AND t.status <> :done")
    long countOpenInProjects(@Param("ids") Collection<Long> ids, @Param("done") TaskStatus done);

    @Query("SELECT COUNT(t) FROM Task t WHERE t.projectId IN :ids AND t.status = :done")
    long countDoneInProjects(@Param("ids") Collection<Long> ids, @Param("done") TaskStatus done);

    @Query("SELECT t.status, COUNT(t) FROM Task t WHERE t.projectId IN :ids GROUP BY t.status")
    List<Object[]> countGroupByStatusInProjects(@Param("ids") Collection<Long> ids);

    @Query("SELECT t FROM Task t WHERE t.projectId IN :ids AND t.dueDate < :today " +
            "AND t.status <> :done ORDER BY t.dueDate ASC")
    List<Task> findOverdueInProjects(@Param("ids") Collection<Long> ids,
                                     @Param("today") LocalDate today,
                                     @Param("done") TaskStatus done);
    // ─── Step 3.3 addition — due-date reminders ───────────────
    @Query("SELECT t FROM Task t WHERE t.dueDate = :due AND t.status <> :done")
    List<Task> findOpenDueOn(@Param("due") LocalDate due, @Param("done") TaskStatus done);

    // ─── Step 5 ───
    List<Task> findByStatus(TaskStatus status);

    /** JPQL update: does not touch updated_at, so "blocked since" stays stable. */
    @Modifying
    @Transactional
    @Query("UPDATE Task t SET t.escalationLevel = :lvl WHERE t.id = :id")
    int updateEscalationLevel(@Param("id") Long id, @Param("lvl") int lvl);
}