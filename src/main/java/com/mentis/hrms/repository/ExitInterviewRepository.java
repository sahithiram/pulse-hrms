package com.mentis.hrms.repository;

import com.mentis.hrms.model.ExitInterview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ExitInterviewRepository extends JpaRepository<ExitInterview, Long> {
    Optional<ExitInterview> findByEmployeeId(String employeeId);
}
