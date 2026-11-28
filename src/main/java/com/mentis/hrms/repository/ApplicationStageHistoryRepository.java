package com.mentis.hrms.repository;

import com.mentis.hrms.model.ApplicationStageHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;

public interface ApplicationStageHistoryRepository extends JpaRepository<ApplicationStageHistory, Long> {
    List<ApplicationStageHistory> findByApplicationIdOrderByChangedAtAsc(Long applicationId);

    @Query("SELECT COUNT(h) FROM ApplicationStageHistory h WHERE h.applicationId = :appId AND h.toStage = 'HIRED'")
    long countHired(Long appId);

    List<ApplicationStageHistory> findByToStageAndChangedAtBetween(String stage, LocalDateTime from, LocalDateTime to);
}
