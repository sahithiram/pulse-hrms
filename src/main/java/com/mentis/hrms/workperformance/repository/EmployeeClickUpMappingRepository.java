package com.mentis.hrms.workperformance.repository;

import com.mentis.hrms.workperformance.model.EmployeeClickUpMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeClickUpMappingRepository extends JpaRepository<EmployeeClickUpMapping, Long> {

    Optional<EmployeeClickUpMapping> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    List<EmployeeClickUpMapping> findByActiveTrue();
}
