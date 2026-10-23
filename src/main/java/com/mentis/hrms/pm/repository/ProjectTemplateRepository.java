package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, Long> {
    List<ProjectTemplate> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
}