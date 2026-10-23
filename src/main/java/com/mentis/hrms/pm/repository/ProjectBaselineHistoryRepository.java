package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectBaselineHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectBaselineHistoryRepository extends JpaRepository<ProjectBaselineHistory, Long> {

    long countByProjectId(Long projectId);

    List<ProjectBaselineHistory> findByProjectIdOrderByVersionNoDesc(Long projectId);
}