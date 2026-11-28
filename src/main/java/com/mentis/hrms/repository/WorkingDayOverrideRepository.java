package com.mentis.hrms.repository;

import com.mentis.hrms.model.WorkingDayOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface WorkingDayOverrideRepository extends JpaRepository<WorkingDayOverride, Long> {
    List<WorkingDayOverride> findByCalendarIdAndOverrideDateBetween(Long calendarId, LocalDate from, LocalDate to);
    List<WorkingDayOverride> findByCalendarIdAndOverrideDate(Long calendarId, LocalDate date);
}
