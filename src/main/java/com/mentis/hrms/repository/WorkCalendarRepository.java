package com.mentis.hrms.repository;

import com.mentis.hrms.model.WorkCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkCalendarRepository extends JpaRepository<WorkCalendar, Long> {
    List<WorkCalendar> findByLocationId(Long locationId);
    Optional<WorkCalendar> findFirstByLocationId(Long locationId);
    Optional<WorkCalendar> findFirstByIsDefaultTrue();
    List<WorkCalendar> findByIsDefaultTrue();
}
