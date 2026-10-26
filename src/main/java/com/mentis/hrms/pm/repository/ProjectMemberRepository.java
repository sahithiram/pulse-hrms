package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectMember;
import com.mentis.hrms.pm.enums.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProjectId(Long projectId);

    List<ProjectMember> findByEmployeeId(Long employeeId);

    Optional<ProjectMember> findByProjectIdAndEmployeeId(Long projectId, Long employeeId);

    /** STEP 7.0: does this person hold this project role on ANY project? (e.g. explicit LEAD) */
    boolean existsByEmployeeIdAndRole(Long employeeId, ProjectRole role);

    void deleteByProjectId(Long projectId);
}