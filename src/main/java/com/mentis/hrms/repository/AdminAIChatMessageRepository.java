package com.mentis.hrms.repository;

import com.mentis.hrms.model.AdminAIChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAIChatMessageRepository extends JpaRepository<AdminAIChatMessage, Integer> {

    List<AdminAIChatMessage> findBySessionIdOrderByCreatedAtAsc(Integer sessionId);

    void deleteBySessionId(Integer sessionId);
}
