package com.mentis.hrms.repository;

import com.mentis.hrms.model.EmployeeDailyMetrics;
import com.mentis.hrms.model.EmployeeDailyMetricsId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface EmployeeDailyMetricsRepository extends JpaRepository<EmployeeDailyMetrics, EmployeeDailyMetricsId> {
    List<EmployeeDailyMetrics> findByEmployeeIdAndMetricsDateBetweenOrderByMetricsDateAsc(
            String employeeId, LocalDate from, LocalDate to);
    List<EmployeeDailyMetrics> findByMetricsDate(LocalDate date);
    void deleteByEmployeeIdAndMetricsDateBetween(String employeeId, LocalDate from, LocalDate to);
}
