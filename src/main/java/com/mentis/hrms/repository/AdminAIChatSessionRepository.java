package com.mentis.hrms.repository;

import com.mentis.hrms.model.AdminAIChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAIChatSessionRepository extends JpaRepository<AdminAIChatSession, Integer> {

    List<AdminAIChatSession> findByEmployeeIdOrderByUpdatedAtDesc(String employeeId);

    long countByEmployeeId(String employeeId);

    void deleteByEmployeeId(String employeeId);
}
