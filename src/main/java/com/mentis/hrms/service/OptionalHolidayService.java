package com.mentis.hrms.service;

import com.mentis.hrms.model.*;
import com.mentis.hrms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Optional ("floating") holidays: employee picks up to N per calendar-year (quota), choice is recorded.
 * Choosing more than the quota is rejected; unchoosing frees one slot.
 */
@Service
public class OptionalHolidayService {

    private static final Logger logger = LoggerFactory.getLogger(OptionalHolidayService.class);

    @Autowired private OptionalHolidayChoiceRepository choiceRepository;
    @Autowired private OptionalHolidayQuotaRepository quotaRepository;
    @Autowired private HolidayCalendarRepository holidayRepository;
    @Autowired private WorkingDayService workingDayService;

    public int quotaFor(Long calendarId, int year) {
        return quotaRepository.findByCalendarIdAndYear(calendarId, year)
                .map(OptionalHolidayQuota::getMaxOptionalPerEmployee)
                .orElse(2);
    }

    public long usedInYear(String employeeId, List<Holiday> optionalHolidaysInYear) {
        if (optionalHolidaysInYear.isEmpty()) return 0;
        List<Long> ids = optionalHolidaysInYear.stream().map(Holiday::getId).collect(Collectors.toList());
        return choiceRepository.findByEmployeeIdAndHolidayIdIn(employeeId, ids).stream()
                .filter(c -> "CHOSEN".equalsIgnoreCase(c.getStatus()))
                .count();
    }

    @Transactional
    public OptionalHolidayChoice choose(String employeeId, Long holidayId) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found: " + holidayId));
        if (holiday.getHolidayType() == null || !holiday.getHolidayType().equalsIgnoreCase("OPTIONAL")) {
            throw new IllegalArgumentException("Holiday is not an optional holiday: " + holidayId);
        }
        Optional<OptionalHolidayChoice> existing = choiceRepository.findByEmployeeIdAndHolidayId(employeeId, holidayId);
        if (existing.isPresent()) {
            OptionalHolidayChoice c = existing.get();
            c.setStatus("CHOSEN");
            choiceRepository.save(c);
            workingDayService.invalidateCache();
            return c;
        }
        int year = holiday.getHolidayDate().getYear();
        List<Holiday> optionalInYear = holidayRepository
                .findByCalendarIdAndHolidayDateBetween(holiday.getCalendarId(),
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .filter(h -> "OPTIONAL".equalsIgnoreCase(h.getHolidayType()))
                .collect(Collectors.toList());
        long used = usedInYear(employeeId, optionalInYear);
        int quota = quotaFor(holiday.getCalendarId(), year);
        if (used >= quota) {
            throw new IllegalStateException("Optional holiday quota exhausted (" + quota + " per year)");
        }
        OptionalHolidayChoice choice = new OptionalHolidayChoice();
        choice.setEmployeeId(employeeId);
        choice.setHolidayId(holidayId);
        choice.setStatus("CHOSEN");
        choiceRepository.save(choice);
        workingDayService.invalidateCache();
        logger.info("Optional holiday {} chosen by {}", holidayId, employeeId);
        return choice;
    }

    @Transactional
    public void unchoose(String employeeId, Long holidayId) {
        choiceRepository.findByEmployeeIdAndHolidayId(employeeId, holidayId).ifPresent(c -> {
            c.setStatus("REJECTED");
            choiceRepository.save(c);
            workingDayService.invalidateCache();
        });
    }

    public List<OptionalHolidayChoice> myChoices(String employeeId) {
        return choiceRepository.findByEmployeeId(employeeId);
    }
}
