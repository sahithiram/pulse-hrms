package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    List<Issue> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<Issue> findByLinkedTaskIdOrderByCreatedAtDesc(Long taskId);

    List<Issue> findByStatusOrderByCreatedAtDesc(String status);

    long countByProjectIdAndStatus(Long projectId, String status);

    long countByStatus(String status);

    @Query("SELECT i FROM Issue i WHERE i.resolvedAt IS NOT NULL AND i.resolvedAt >= :from")
    List<Issue> findResolvedSince(@Param("from") LocalDateTime from);

    @Query("SELECT i.severity, COUNT(i) FROM Issue i WHERE i.projectId = :pid GROUP BY i.severity")
    List<Object[]> countBySeverityForProject(@Param("pid") Long projectId);

    @Query("SELECT i.type, COUNT(i) FROM Issue i WHERE i.projectId = :pid GROUP BY i.type")
    List<Object[]> countByTypeForProject(@Param("pid") Long projectId);
}