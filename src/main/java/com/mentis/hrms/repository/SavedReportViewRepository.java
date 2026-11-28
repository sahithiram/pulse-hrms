package com.mentis.hrms.repository;

import com.mentis.hrms.model.SavedReportView;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SavedReportViewRepository extends JpaRepository<SavedReportView, Long> {
    List<SavedReportView> findByUserIdAndReportKeyOrderByNameAsc(String userId, String reportKey);
    Optional<SavedReportView> findByUserIdAndReportKeyAndName(String userId, String reportKey, String name);
    void deleteByIdAndUserId(Long id, String userId);
}
