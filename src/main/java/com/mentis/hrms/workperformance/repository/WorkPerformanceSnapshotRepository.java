package com.mentis.hrms.workperformance.repository;

import com.mentis.hrms.workperformance.model.WorkPerformanceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkPerformanceSnapshotRepository extends JpaRepository<WorkPerformanceSnapshot, Long> {

    Optional<WorkPerformanceSnapshot> findByEmployeeIdAndReviewMonth(String employeeId, LocalDate reviewMonth);

    // Used for fairness: get every employee's raw task volume in the same
    // department & month, so one person isn't scored against the whole company.
    List<WorkPerformanceSnapshot> findByDepartmentAndReviewMonth(String department, LocalDate reviewMonth);

    List<WorkPerformanceSnapshot> findByReviewMonthOrderByFinalCombinedScoreDesc(LocalDate reviewMonth);
}
