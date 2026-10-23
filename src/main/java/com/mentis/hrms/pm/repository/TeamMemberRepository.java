package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamId(Long teamId);
    List<TeamMember> findByEmployeeId(Long employeeId);
    Optional<TeamMember> findByTeamIdAndEmployeeId(Long teamId, Long employeeId);
    long countByTeamId(Long teamId);

    void deleteByTeamId(Long teamId);
}