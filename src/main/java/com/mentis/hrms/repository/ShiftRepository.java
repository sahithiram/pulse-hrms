package com.mentis.hrms.repository;

import com.mentis.hrms.model.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
    Optional<Shift> findByName(String name);
}
