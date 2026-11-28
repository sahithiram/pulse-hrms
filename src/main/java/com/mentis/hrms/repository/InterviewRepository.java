package com.mentis.hrms.repository;

import com.mentis.hrms.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByApplicationIdOrderByScheduledAtAsc(Long applicationId);
    List<Interview> findByStatusAndScheduledAtBefore(String status, LocalDateTime cutoff);
}
