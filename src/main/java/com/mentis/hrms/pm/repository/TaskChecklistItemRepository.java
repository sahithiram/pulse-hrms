// TaskChecklistItemRepository.java
package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TaskChecklistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {
    List<TaskChecklistItem> findByTaskIdOrderBySortOrderAsc(Long taskId);
    List<TaskChecklistItem> findByTaskIdIn(Collection<Long> taskIds);
    void deleteByTaskId(Long taskId);
}