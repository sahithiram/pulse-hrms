// TaskDependencyRepository.java
package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByTaskId(Long taskId);
    List<TaskDependency> findByTaskIdIn(Collection<Long> taskIds);
    void deleteByTaskId(Long taskId);
    void deleteByDependsOnTaskId(Long dependsOnTaskId);

    /** PM-019: true when `taskId` is a direct blocker of any of `myTaskIds`, or blocked by one of them. */
    @org.springframework.data.jpa.repository.Query(
            "select count(d) > 0 from TaskDependency d where " +
            "(d.taskId in :myTaskIds and d.dependsOnTaskId = :taskId) or " +
            "(d.dependsOnTaskId in :myTaskIds and d.taskId = :taskId)")
    boolean existsBetweenAny(@org.springframework.data.repository.query.Param("myTaskIds") Collection<Long> myTaskIds,
                             @org.springframework.data.repository.query.Param("taskId") Long taskId);
}