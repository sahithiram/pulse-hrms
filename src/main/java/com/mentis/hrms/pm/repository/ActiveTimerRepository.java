package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ActiveTimer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActiveTimerRepository extends JpaRepository<ActiveTimer, Long> {
    Optional<ActiveTimer> findByEmployeeId(Long employeeId);
}