package com.mentis.hrms.repository;

import com.mentis.hrms.model.EmployeeJobHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeJobHistoryRepository extends JpaRepository<EmployeeJobHistory, Long> {
    List<EmployeeJobHistory> findByEmployeeIdOrderByEffectiveFromDesc(Long employeeId);
    Optional<EmployeeJobHistory> findFirstByEmployeeIdAndEffectiveToIsNull(Long employeeId);
    List<EmployeeJobHistory> findByEmployeeIdAndEffectiveFromLessThanEqualAndEffectiveToGreaterThanEqual(
            Long employeeId, LocalDate at, LocalDate at2);
}
