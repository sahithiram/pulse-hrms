package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    Optional<LeaveType> findByCode(String code);
}
