package com.mentis.hrms.repository;

import com.mentis.hrms.model.ApplicationSource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ApplicationSourceRepository extends JpaRepository<ApplicationSource, Long> {
    Optional<ApplicationSource> findByApplicationId(Long applicationId);
    List<ApplicationSource> findBySource(String source);
}
