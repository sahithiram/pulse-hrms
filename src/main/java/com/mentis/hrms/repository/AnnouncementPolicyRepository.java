package com.mentis.hrms.repository;

import com.mentis.hrms.model.AnnouncementPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnnouncementPolicyRepository extends JpaRepository<AnnouncementPolicy, Long> {
    List<AnnouncementPolicy> findByRequiresAckTrue();
}
