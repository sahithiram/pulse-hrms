package com.mentis.hrms.repository;

import com.mentis.hrms.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    List<Job> findByActiveTrue();
    List<Job> findByDepartment(String department);

    // Add this method if it doesn't exist
    boolean existsById(Long id);

    @Query("""
            SELECT j FROM Job j
            LEFT JOIN j.jobLocation loc
            WHERE (:search IS NULL OR :search = ''
                   OR LOWER(j.title) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.department) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(j.location) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(loc.name) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(loc.city) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(loc.country) LIKE LOWER(CONCAT('%', :search, '%')))
              AND (:active IS NULL OR COALESCE(j.active, true) = :active)
              AND (:department IS NULL OR :department = '' OR LOWER(j.department) = LOWER(:department))
            """)
    Page<Job> findFilteredJobs(@Param("search") String search,
                               @Param("active") Boolean active,
                               @Param("department") String department,
                               Pageable pageable);

    @Query("""
            SELECT j FROM Job j
            WHERE COALESCE(j.active, true) = true
              AND (j.applicationDeadline IS NULL OR j.applicationDeadline > :today)
            """)
    Page<Job> findActiveJobsForOverview(@Param("today") LocalDate today, Pageable pageable);

    @Query("SELECT DISTINCT j.department FROM Job j WHERE j.department IS NOT NULL AND j.department <> '' ORDER BY j.department ASC")
    List<String> findDistinctDepartments();

    @Query("SELECT COUNT(j) FROM Job j WHERE COALESCE(j.active, true) = true")
    long countActiveJobs();
}