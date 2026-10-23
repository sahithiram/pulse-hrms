// TaskCommentRepository.java
package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskIdAndDeletedFalseOrderByCreatedAtAsc(Long taskId);
    long countByTaskIdAndDeletedFalse(Long taskId);
}