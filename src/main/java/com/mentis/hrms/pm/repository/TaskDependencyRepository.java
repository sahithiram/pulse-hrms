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
}