package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeaveRequestDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only bridge into Sprint-4's leave_request_day (attendance does NOT write leave rows).
 */
public interface LeaveRequestDayReadRepository extends JpaRepository<LeaveRequestDay, Long> {

    @Query("SELECT COALESCE(SUM(d.dayFraction), 0) FROM LeaveRequestDay d WHERE d.day = :day AND d.counted = true " +
           "AND d.requestId IN (SELECT r.id FROM LeaveRequest r WHERE r.employeeId = :employeeId AND r.status = 'APPROVED')")
    BigDecimal sumCountedLeaveFraction(@Param("employeeId") String employeeId, @Param("day") LocalDate day);
}
