package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeavePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeavePolicyRepository extends JpaRepository<LeavePolicy, Long> {
    List<LeavePolicy> findByLeaveTypeId(Long leaveTypeId);
}
