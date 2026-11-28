package com.mentis.hrms.repository;

import com.mentis.hrms.model.RosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, Long> {
    Optional<RosterEntry> findByEmployeeIdAndRosterDate(String employeeId, LocalDate rosterDate);
}
