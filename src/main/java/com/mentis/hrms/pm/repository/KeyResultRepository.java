package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.KeyResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KeyResultRepository extends JpaRepository<KeyResult, Long> {
    List<KeyResult> findByGoalIdOrderByCreatedAtAsc(Long goalId);
    void deleteByGoalId(Long goalId);
}