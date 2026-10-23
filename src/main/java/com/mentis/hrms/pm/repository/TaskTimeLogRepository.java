package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TaskTimeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TaskTimeLogRepository extends JpaRepository<TaskTimeLog, Long> {

    List<TaskTimeLog> findByTaskIdOrderByLogDateDesc(Long taskId);

    @Query("select coalesce(sum(t.minutes), 0) from TaskTimeLog t where t.taskId = :id")
    long sumMinutes(@Param("id") Long id);

    @Modifying
    @Query("delete from TaskTimeLog t where t.taskId = :taskId")
    void deleteByTaskId(@Param("taskId") Long taskId);

    // ─── PHASE 6 additions ─────────────────────────────────────

    List<TaskTimeLog> findByEmployeeIdAndLogDateBetweenOrderByLogDateAsc(
            Long employeeId, LocalDate from, LocalDate to);

    @Query("select coalesce(sum(t.minutes), 0) from TaskTimeLog t " +
            "where t.employeeId = :empId and t.logDate between :from and :to")
    long sumMinutesByEmployeeAndRange(
            @Param("empId") Long empId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("select t from TaskTimeLog t " +
            "where t.taskId in :taskIds and t.employeeId = :empId " +
            "order by t.logDate asc")
    List<TaskTimeLog> findEmployeeLogsForTasks(
            @Param("taskIds") List<Long> taskIds,
            @Param("empId") Long empId);
    /** All time logs booked on tasks of one project. */
    @Query("select l from TaskTimeLog l, Task t where l.taskId = t.id and t.projectId = :projectId")
    List<TaskTimeLog> findForProject(@Param("projectId") Long projectId);
}