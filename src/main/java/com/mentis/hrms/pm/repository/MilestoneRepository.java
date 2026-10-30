package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Milestone;
import com.mentis.hrms.pm.enums.MilestoneStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {
    List<Milestone> findByProjectIdOrderByDueDateAsc(Long projectId);
    List<Milestone> findByStatus(MilestoneStatus status);
    List<Milestone> findByDueDateBetween(LocalDate from, LocalDate to);
    List<Milestone> findByProjectIdAndStatusAndDueDateBetween(
            Long projectId, MilestoneStatus status, LocalDate from, LocalDate to);
}