package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.TimesheetWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TimesheetWeekRepository extends JpaRepository<TimesheetWeek, Long> {
    Optional<TimesheetWeek> findByEmployeeIdAndWeekStart(Long employeeId, LocalDate weekStart);
}