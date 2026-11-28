package com.mentis.hrms.repository;

import com.mentis.hrms.model.ApplicationMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ApplicationMetaRepository extends JpaRepository<ApplicationMeta, Long> {
    List<ApplicationMeta> findByResumeHash(String resumeHash);
    List<ApplicationMeta> findByPhoneNormalized(String phoneNormalized);
    List<ApplicationMeta> findByTalentPoolTrue();

    @Query(value = "SELECT " +
            "  (SELECT COUNT(*) FROM application_meta WHERE talent_pool = 1) AS pool, " +
            "  (SELECT COUNT(*) FROM application_meta WHERE rejection_reason IS NOT NULL) AS rejected_marked",
            nativeQuery = true)
    Object[] poolStats();
}
