// TaskWatcherRepository.java
package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TaskWatcher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskWatcherRepository extends JpaRepository<TaskWatcher, Long> {
    List<TaskWatcher> findByTaskId(Long taskId);
    List<TaskWatcher> findByEmployeeId(Long employeeId);
    boolean existsByTaskIdAndEmployeeId(Long taskId, Long employeeId);
    void deleteByTaskIdAndEmployeeId(Long taskId, Long employeeId);
    void deleteByTaskId(Long taskId);
}