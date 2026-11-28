package com.mentis.hrms.repository;

import com.mentis.hrms.model.Holiday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

/** ADDITIVE calendar-aware queries; existing HolidayRepository is left untouched. */
public interface HolidayCalendarRepository extends JpaRepository<Holiday, Long> {
    List<Holiday> findByCalendarIdAndHolidayDateBetween(Long calendarId, LocalDate from, LocalDate to);
    List<Holiday> findByCalendarIdAndHolidayDate(Long calendarId, LocalDate date);
    List<Holiday> findByCalendarIdAndHolidayDateBetweenOrderByHolidayDateAsc(Long calendarId, LocalDate from, LocalDate to);
}
