package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Risk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RiskRepository extends JpaRepository<Risk, Long> {

    List<Risk> findByProjectIdOrderByScoreDesc(Long projectId);
    List<Risk> findByStatusOrderByScoreDesc(String status);
    List<Risk> findByScoreGreaterThanEqualAndStatusNotOrderByScoreDesc(int score, String status);
    List<Risk> findByReviewDateBetween(LocalDate from, LocalDate to);
    long countByProjectIdAndScoreGreaterThanEqual(Long projectId, int score);
}