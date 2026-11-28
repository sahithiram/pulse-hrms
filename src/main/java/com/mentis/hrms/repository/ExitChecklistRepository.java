package com.mentis.hrms.repository;

import com.mentis.hrms.model.ExitChecklist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExitChecklistRepository extends JpaRepository<ExitChecklist, Long> {
    List<ExitChecklist> findByEmployeeIdOrderByTaskAsc(String employeeId);
    Optional<ExitChecklist> findByEmployeeIdAndTask(String employeeId, String task);
    long countByEmployeeIdAndStatusNot(String employeeId, String status);
}
