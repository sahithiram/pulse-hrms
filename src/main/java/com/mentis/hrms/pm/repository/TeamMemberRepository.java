package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    List<TeamMember> findByTeamId(Long teamId);
    List<TeamMember> findByEmployeeId(Long employeeId);
    Optional<TeamMember> findByTeamIdAndEmployeeId(Long teamId, Long employeeId);
    long countByTeamId(Long teamId);

    void deleteByTeamId(Long teamId);

    // ── PERF additions (permission checks and list screens) ──────────────

    /** yes/no membership test without loading the whole member list */
    boolean existsByTeamIdAndEmployeeId(Long teamId, Long employeeId);

    /** is the employee a member of ANY of these teams */
    boolean existsByEmployeeIdAndTeamIdIn(Long employeeId, Collection<Long> teamIds);

    /** [teamId, memberCount] for many teams in one query */
    @Query("select m.teamId, count(m) from TeamMember m where m.teamId in :ids group by m.teamId")
    List<Object[]> countByTeamIds(@Param("ids") Collection<Long> ids);
}
