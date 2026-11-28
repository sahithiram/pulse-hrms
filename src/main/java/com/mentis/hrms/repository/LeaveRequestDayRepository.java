package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeaveRequestDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LeaveRequestDayRepository extends JpaRepository<LeaveRequestDay, Long> {
    List<LeaveRequestDay> findByRequestId(String requestId);
    List<LeaveRequestDay> findByRequestIdIn(List<String> requestIds);
    void deleteByRequestId(String requestId);
    boolean existsByRequestIdAndCounted(String requestId, boolean counted);
}
