package com.mentis.hrms.repository;

import com.mentis.hrms.model.AttendanceRegularization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface AttendanceRegularizationRepository extends JpaRepository<AttendanceRegularization, Long> {
    List<AttendanceRegularization> findByStatusOrderByIdDesc(String status);
    List<AttendanceRegularization> findByEmployeeIdOrderByIdDesc(String employeeId);

    @Query("SELECT COUNT(r) FROM AttendanceRegularization r WHERE r.employeeId = :employeeId " +
           "AND r.attendanceDate BETWEEN :from AND :to AND r.status IN ('PENDING','APPROVED')")
    long countRequestsInRange(@Param("employeeId") String employeeId,
                              @Param("from") LocalDate from,
                              @Param("to") LocalDate to);
}
