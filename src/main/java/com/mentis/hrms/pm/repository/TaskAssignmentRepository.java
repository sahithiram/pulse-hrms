package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

    List<TaskAssignment> findByTaskId(Long taskId);

    List<TaskAssignment> findByTaskIdIn(java.util.Collection<Long> taskIds);

    List<TaskAssignment> findByEmployeeId(Long employeeId);

    /** PERF: permission checks only need yes/no, not the whole list */
    boolean existsByTaskIdAndEmployeeId(Long taskId, Long employeeId);

    void deleteByTaskId(Long taskId);

    /** [employeeId, openTaskCount] for everyone with at least one task that is not DONE, busiest first */
    @Query("select a.employeeId, count(a) from TaskAssignment a, Task t " +
            "where a.taskId = t.id and t.status <> com.mentis.hrms.pm.enums.TaskStatus.DONE " +
            "group by a.employeeId order by count(a) desc")
    List<Object[]> countOpenByEmployee();

    /** PM-019: the task ids I am assigned to (used for member visibility + the member board) */
    @Query("select a.taskId from TaskAssignment a where a.employeeId = :employeeId")
    List<Long> findTaskIdsByEmployeeId(@Param("employeeId") Long employeeId);

    /** true when the employee is assigned to at least one task of the project */
    @Query("select count(a) > 0 from TaskAssignment a, Task t " +
            "where a.taskId = t.id and t.projectId = :projectId and a.employeeId = :employeeId")
    boolean existsByProjectAndEmployee(@Param("projectId") Long projectId,
                                       @Param("employeeId") Long employeeId);
}