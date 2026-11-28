package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeaveCancellationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeaveCancellationRequestRepository extends JpaRepository<LeaveCancellationRequest, Long> {
    List<LeaveCancellationRequest> findByLeaveRequestIdOrderByIdDesc(String leaveRequestId);
    List<LeaveCancellationRequest> findByStatusOrderByIdDesc(String status);
    List<LeaveCancellationRequest> findByRequestedByOrderByIdDesc(String requestedBy);
}
