package com.mentis.hrms.repository;

import com.mentis.hrms.model.OfferWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OfferWorkflowRepository extends JpaRepository<OfferWorkflow, Long> {
    Optional<OfferWorkflow> findByOfferLetterId(Long offerLetterId);
    List<OfferWorkflow> findByStatusAndValidUntilBefore(String status, LocalDate cutoff);
    long countByStatus(String status);
}
