package com.mentis.hrms.repository;

import com.mentis.hrms.model.HrAIChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HrAIChatMessageRepository extends JpaRepository<HrAIChatMessage, Integer> {
    List<HrAIChatMessage> findBySessionIdOrderByCreatedAtAsc(Integer sessionId);
}
