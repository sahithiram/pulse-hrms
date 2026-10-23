package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByApproverIdOrderByCreatedAtDesc(Long approverId);
    List<ApprovalRequest> findByRequestedByOrderByCreatedAtDesc(Long requestedBy);
    List<ApprovalRequest> findByApproverIdAndStatusOrderByCreatedAtDesc(Long approverId, String status);
    List<ApprovalRequest> findByStatusAndCreatedAtBefore(String status, LocalDateTime cutoff);
    List<ApprovalRequest> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);
    List<ApprovalRequest> findByStatusOrderByCreatedAtDesc(String status);
    long countByApproverIdAndStatus(Long approverId, String status);

    // ─── Step 2.3 addition ────────────────────────────────────
    boolean existsByEntityTypeAndEntityIdAndStatus(String entityType, Long entityId, String status);
}