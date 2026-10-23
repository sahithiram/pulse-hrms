package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    List<ProjectMember> findByProjectId(Long projectId);

    List<ProjectMember> findByEmployeeId(Long employeeId);

    Optional<ProjectMember> findByProjectIdAndEmployeeId(Long projectId, Long employeeId);

    void deleteByProjectId(Long projectId);
}