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

    List<TaskAssignment> findByEmployeeId(Long employeeId);

    void deleteByTaskId(Long taskId);

    /** true when the employee is assigned to at least one task of the project */
    @Query("select count(a) > 0 from TaskAssignment a, Task t " +
            "where a.taskId = t.id and t.projectId = :projectId and a.employeeId = :employeeId")
    boolean existsByProjectAndEmployee(@Param("projectId") Long projectId,
                                       @Param("employeeId") Long employeeId);
}