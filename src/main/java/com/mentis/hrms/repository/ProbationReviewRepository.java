package com.mentis.hrms.repository;

import com.mentis.hrms.model.ProbationReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface ProbationReviewRepository extends JpaRepository<ProbationReview, Long> {
    List<ProbationReview> findByStatusOrderByDueDateAsc(String status);
    boolean existsByEmployeeIdAndStatus(String employeeId, String status);
    List<ProbationReview> findByStatusAndDueDateLessThanEqual(String status, LocalDate cutoff);
}
