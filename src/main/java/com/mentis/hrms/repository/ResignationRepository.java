package com.mentis.hrms.repository;

import com.mentis.hrms.model.Resignation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ResignationRepository extends JpaRepository<Resignation, Long> {
    Optional<Resignation> findByEmployeeIdAndStatus(String employeeId, String status);
    List<Resignation> findByStatusAndAcceptedLastDayLessThanEqual(String status, LocalDate cutoff);
    List<Resignation> findTop50ByOrderByCreatedAtDesc();
}
