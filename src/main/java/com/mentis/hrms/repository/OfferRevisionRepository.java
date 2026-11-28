package com.mentis.hrms.repository;

import com.mentis.hrms.model.OfferRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OfferRevisionRepository extends JpaRepository<OfferRevision, Long> {
    List<OfferRevision> findByOfferLetterIdOrderByVersionDesc(Long offerLetterId);
}
