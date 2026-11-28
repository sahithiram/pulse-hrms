package com.mentis.hrms.repository;

import com.mentis.hrms.model.OptionalHolidayQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OptionalHolidayQuotaRepository extends JpaRepository<OptionalHolidayQuota, Long> {
    Optional<OptionalHolidayQuota> findByCalendarIdAndYear(Long calendarId, Integer year);
}
