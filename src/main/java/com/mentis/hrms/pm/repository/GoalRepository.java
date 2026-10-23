package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Goal;
import com.mentis.hrms.pm.enums.GoalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<Goal> findByOwnerId(Long ownerId);
    List<Goal> findByQuarterAndYear(String quarter, Integer year);
    List<Goal> findByStatus(GoalStatus status);
}