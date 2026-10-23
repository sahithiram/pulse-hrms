package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
    List<ProjectRequest> findAllByOrderByIdDesc();
    List<ProjectRequest> findByCreatedByOrderByIdDesc(Long createdBy);
}