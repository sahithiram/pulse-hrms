package com.mentis.hrms.repository;

import com.mentis.hrms.model.EmployeeShift;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface EmployeeShiftRepository extends JpaRepository<EmployeeShift, Long> {
    List<EmployeeShift> findByEmployeeId(String employeeId);
    List<EmployeeShift> findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(String employeeId, LocalDate date);
}
