package com.mentis.hrms.EmployeePerformance.repository;

import com.mentis.hrms.EmployeePerformance.model.AdminEmpPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdminEmpPerformanceRepository extends JpaRepository<AdminEmpPerformance, Long> {

    // Find performance by employee and month
    Optional<AdminEmpPerformance> findByEmployeeIdAndReviewMonth(String employeeId, LocalDate reviewMonth);

    // Find all performances for a specific month (sorted by score descending)
    List<AdminEmpPerformance> findByReviewMonthOrderByTotalScoreDesc(LocalDate reviewMonth);

    // Find all performances for a specific employee (history)
    List<AdminEmpPerformance> findByEmployeeIdOrderByReviewMonthDesc(String employeeId);

    // Find performances by rating
    List<AdminEmpPerformance> findByPerformanceRating(String rating);

    // Find performances by department and month
    List<AdminEmpPerformance> findByDepartmentAndReviewMonth(String department, LocalDate reviewMonth);

    // Get distinct months with performance records
    @Query("SELECT DISTINCT p.reviewMonth FROM AdminEmpPerformance p ORDER BY p.reviewMonth DESC")
    List<LocalDate> findDistinctReviewMonths();

    // Count by rating for a month
    @Query("SELECT p.performanceRating, COUNT(p) FROM AdminEmpPerformance p WHERE p.reviewMonth = :month GROUP BY p.performanceRating")
    List<Object[]> countByRatingForMonth(@Param("month") LocalDate month);

    // Find top performers for a month
    @Query("SELECT p FROM AdminEmpPerformance p WHERE p.reviewMonth = :month ORDER BY p.totalScore DESC")
    List<AdminEmpPerformance> findTopPerformersForMonth(@Param("month") LocalDate month);

    // Check if performance exists for employee and month
    boolean existsByEmployeeIdAndReviewMonth(String employeeId, LocalDate reviewMonth);
}
