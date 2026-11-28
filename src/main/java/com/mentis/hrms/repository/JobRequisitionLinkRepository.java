package com.mentis.hrms.repository;

import com.mentis.hrms.model.JobRequisitionLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface JobRequisitionLinkRepository extends JpaRepository<JobRequisitionLink, Long> {
    Optional<JobRequisitionLink> findByRequisitionId(Long requisitionId);
}
