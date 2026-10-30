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
            " OR p.teamId IN (SELECT tm.teamId FROM TeamMember tm WHERE tm.employeeId = :viewerId) " +
            " OR p.teamId IN (SELECT t.id FROM Team t WHERE t.leadEmployeeId = :viewerId) " +
            " OR p.id IN (SELECT pm.projectId FROM ProjectMember pm WHERE pm.employeeId = :viewerId) " +
            " OR p.id IN (SELECT tk.projectId FROM Task tk, TaskAssignment a " +
            "             WHERE a.taskId = tk.id AND a.employeeId = :viewerId))")
    Page<Project> searchVisible(@Param("search") String search,
                                @Param("status") ProjectStatus status,
                                @Param("ownerId") Long ownerId,
                                @Param("viewerId") Long viewerId,
                                Pageable pageable);

}