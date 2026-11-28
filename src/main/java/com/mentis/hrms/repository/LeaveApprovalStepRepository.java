package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeaveApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeaveApprovalStepRepository extends JpaRepository<LeaveApprovalStep, Long> {
    List<LeaveApprovalStep> findByRequestIdOrderByStepNoAsc(String requestId);
    List<LeaveApprovalStep> findByApproverIdAndStatus(String approverId, String status);
}
