package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Highest numeric suffix among codes shaped PRJ-&lt;digits&gt;; 1000 when there are none
     * (so the first auto-generated code is PRJ-1001).
     * <p>
     * MySQL-only: uses SUBSTRING(code, 5), CAST AS UNSIGNED and REGEXP. This project is
     * configured with MySQLDialect in application.properties, so this is safe here.
     */
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(code, 5) AS UNSIGNED)), 1000) " +
            "FROM pm_projects WHERE code REGEXP '^PRJ-[0-9]+$'", nativeQuery = true)
    Number findMaxNumericCode();

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    long countByStatus(ProjectStatus status);

    long countByHealth(com.mentis.hrms.pm.enums.ProjectHealth health);

    List<Project> findByOwnerId(Long ownerId);

    @Query("SELECT p FROM Project p WHERE " +
            "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:ownerId IS NULL OR p.ownerId = :ownerId)")
    Page<Project> search(@Param("search") String search,
                         @Param("status") ProjectStatus status,
                         @Param("ownerId") Long ownerId,
                         Pageable pageable);

    @Query("SELECT p FROM Project p WHERE p.ownerId = :ownerId " +
            "AND p.status <> com.mentis.hrms.pm.enums.ProjectStatus.COMPLETED " +
            "AND p.status <> com.mentis.hrms.pm.enums.ProjectStatus.ARCHIVED")
    List<Project> findActiveByOwnerId(@Param("ownerId") Long ownerId);

    @Query("SELECT p FROM Project p WHERE " +
            "(:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:ownerId IS NULL OR p.ownerId = :ownerId) " +
            "AND (p.ownerId = :viewerId " +
            // legacy single-team: primary team_id
            " OR p.teamId IN (SELECT tm.teamId FROM TeamMember tm WHERE tm.employeeId = :viewerId) " +
            " OR p.teamId IN (SELECT t.id FROM Team t WHERE t.leadEmployeeId = :viewerId) " +
            // NEW multi-team: any team linked via pm_project_teams
            " OR p.id IN (SELECT pt.projectId FROM ProjectTeam pt WHERE pt.teamId IN " +
            "             (SELECT tm2.teamId FROM TeamMember tm2 WHERE tm2.employeeId = :viewerId)) " +
            " OR p.id IN (SELECT pt2.projectId FROM ProjectTeam pt2 WHERE pt2.teamId IN " +
            "             (SELECT t2.id FROM Team t2 WHERE t2.leadEmployeeId = :viewerId)) " +
            // explicit project membership / task assignment
            " OR p.id IN (SELECT pm.projectId FROM ProjectMember pm WHERE pm.employeeId = :viewerId) " +
            " OR p.id IN (SELECT tk.projectId FROM Task tk, TaskAssignment a " +
            "             WHERE a.taskId = tk.id AND a.employeeId = :viewerId))")
    Page<Project> searchVisible(@Param("search") String search,
                                @Param("status") ProjectStatus status,
                                @Param("ownerId") Long ownerId,
                                @Param("viewerId") Long viewerId,
                                Pageable pageable);
}