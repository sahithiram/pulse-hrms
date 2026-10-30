package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectPhase;
import com.mentis.hrms.pm.enums.ProjectPhaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, Long> {
    List<ProjectPhase> findByProjectIdOrderByStartDateAsc(Long projectId);

    List<ProjectPhase> findByProjectIdAndStatusNotOrderByStartDateAsc(Long projectId, ProjectPhaseStatus status);
}
