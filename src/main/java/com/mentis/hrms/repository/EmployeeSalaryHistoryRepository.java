package com.mentis.hrms.repository;

import com.mentis.hrms.model.EmployeeSalaryHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EmployeeSalaryHistoryRepository extends JpaRepository<EmployeeSalaryHistory, Long> {
    List<EmployeeSalaryHistory> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
}
