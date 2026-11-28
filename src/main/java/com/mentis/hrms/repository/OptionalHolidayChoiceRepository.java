package com.mentis.hrms.repository;

import com.mentis.hrms.model.OptionalHolidayChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OptionalHolidayChoiceRepository extends JpaRepository<OptionalHolidayChoice, Long> {
    List<OptionalHolidayChoice> findByEmployeeId(String employeeId);
    List<OptionalHolidayChoice> findByEmployeeIdAndHolidayIdIn(String employeeId, List<Long> holidayIds);
    Optional<OptionalHolidayChoice> findByEmployeeIdAndHolidayId(String employeeId, Long holidayId);
    long countByEmployeeIdAndStatus(String employeeId, String status);
}
