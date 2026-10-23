package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectClosureItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectClosureItemRepository extends JpaRepository<ProjectClosureItem, Long> {

    List<ProjectClosureItem> findByProjectIdOrderByIdAsc(Long projectId);

    long countByProjectId(Long projectId);

    long countByProjectIdAndDoneFalse(Long projectId);

    void deleteByProjectId(Long projectId);
}