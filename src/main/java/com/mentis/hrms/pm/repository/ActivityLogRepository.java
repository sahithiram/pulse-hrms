// ActivityLogRepository.java
package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
    Page<ActivityLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);
    List<ActivityLog> findTop20ByOrderByCreatedAtDesc();
    List<ActivityLog> findTop100ByOrderByCreatedAtDesc();
    /** Phase 5 — burndown: all STATUS_CHANGED entries for a set of tasks in a date window. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT a FROM ActivityLog a " +
                    "WHERE a.entityType = 'TASK' " +
                    "AND a.action = 'STATUS_CHANGED' " +
                    "AND a.newValue = 'DONE' " +
                    "AND a.entityId IN :taskIds " +
                    "AND a.createdAt >= :from " +
                    "ORDER BY a.createdAt ASC")
    List<ActivityLog> findDoneTransitions(
            @org.springframework.data.repository.query.Param("taskIds") java.util.Collection<Long> taskIds,
            @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);
}