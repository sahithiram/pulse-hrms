package com.mentis.hrms.repository;

import com.mentis.hrms.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByOccurredAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<AuditLog> findByActorIdAndOccurredAtBetween(Long actorId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<AuditLog> findByActionAndOccurredAtBetween(String action, LocalDateTime start, LocalDateTime end, Pageable pageable);
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
}
