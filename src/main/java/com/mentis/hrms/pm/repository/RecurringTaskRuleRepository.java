package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.RecurringTaskRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface RecurringTaskRuleRepository extends JpaRepository<RecurringTaskRule, Long> {
    List<RecurringTaskRule> findByProjectIdOrderByIdDesc(Long projectId);
    List<RecurringTaskRule> findByPausedFalseAndNextRunDateLessThanEqual(LocalDate date);
    List<RecurringTaskRule> findByProjectIdAndPausedFalseAndNextRunDateLessThanEqual(Long projectId, LocalDate date);
}