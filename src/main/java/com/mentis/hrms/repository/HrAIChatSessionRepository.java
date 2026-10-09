package com.mentis.hrms.repository;

import com.mentis.hrms.model.HrAIChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HrAIChatSessionRepository extends JpaRepository<HrAIChatSession, Integer> {
    List<HrAIChatSession> findByEmployeeIdOrderByUpdatedAtDesc(String employeeId);
}
