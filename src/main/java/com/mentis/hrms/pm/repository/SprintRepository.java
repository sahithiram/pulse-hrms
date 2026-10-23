package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Sprint;
import com.mentis.hrms.pm.enums.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectIdOrderByStartDateDesc(Long projectId);
    List<Sprint> findByProjectIdAndStatusOrderByStartDateDesc(Long projectId, SprintStatus status);
    List<Sprint> findByStatus(SprintStatus status);
}