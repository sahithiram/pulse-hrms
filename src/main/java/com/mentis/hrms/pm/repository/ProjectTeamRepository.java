package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProjectTeamRepository extends JpaRepository<ProjectTeam, Long> {

    List<ProjectTeam> findByProjectIdOrderByIdAsc(Long projectId);

    List<ProjectTeam> findByProjectIdIn(Collection<Long> projectIds);

    void deleteByProjectId(Long projectId);

    void deleteByTeamId(Long teamId);

    @Query("SELECT DISTINCT pt.projectId FROM ProjectTeam pt WHERE pt.teamId IN :teamIds")
    List<Long> findProjectIdsByTeamIdIn(@Param("teamIds") Collection<Long> teamIds);
}