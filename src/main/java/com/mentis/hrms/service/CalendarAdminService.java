package com.mentis.hrms.service;

import com.mentis.hrms.model.*;
import com.mentis.hrms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** HR calendar administration (REQ-3-09). All mutations invalidate the working-day cache. */
@Service
public class CalendarAdminService {

    private static final Logger logger = LoggerFactory.getLogger(CalendarAdminService.class);

    @Autowired private WorkCalendarRepository calendarRepository;
    @Autowired private WeeklyOffRuleRepository weeklyOffRuleRepository;
    @Autowired private HolidayCalendarRepository holidayRepository;
    @Autowired private WorkingDayOverrideRepository overrideRepository;
    @Autowired private OptionalHolidayQuotaRepository quotaRepository;
    @Autowired private WorkingDayService workingDayService;

    // ---- calendars ----
    public List<WorkCalendar> listCalendars() {
        return calendarRepository.findAll();
    }

    @Transactional
    public WorkCalendar createCalendar(String name, Long locationId, boolean isDefault,
                                       LocalDate validFrom, LocalDate validTo) {
        if (isDefault) {
            // Only one default: demote any existing default
            calendarRepository.findByIsDefaultTrue().forEach(c -> { c.setDefault(false); calendarRepository.save(c); });
        }
        WorkCalendar c = new WorkCalendar();
        c.setName(name);
        c.setLocationId(locationId);
        c.setDefault(isDefault);
        c.setValidFrom(validFrom);
        c.setValidTo(validTo);
        WorkCalendar saved = calendarRepository.save(c);
        workingDayService.invalidateCache();
        return saved;
    }

    // ---- weekly off rules ----
    public List<WeeklyOffRule> weeklyOffRules(Long calendarId) {
        return weeklyOffRuleRepository.findByCalendarId(calendarId);
    }

    @Transactional
    public WeeklyOffRule addWeeklyOffRule(Long calendarId, int dayOfWeekIso, String pattern) {
        if (dayOfWeekIso < 1 || dayOfWeekIso > 7) throw new IllegalArgumentException("dayOfWeek must be 1..7 (ISO)");
        String p = (pattern == null ? "ALL" : pattern.trim().toUpperCase());
        Set<String> allowed = Set.of("ALL","ODD","EVEN","FIRST","SECOND","THIRD","FOURTH","FIFTH","LAST");
        if (!allowed.contains(p)) throw new IllegalArgumentException("pattern must be one of " + allowed);
        WeeklyOffRule rule = new WeeklyOffRule();
        rule.setCalendarId(calendarId);
        rule.setDayOfWeek(dayOfWeekIso);
        rule.setPattern(p);
        WeeklyOffRule saved = weeklyOffRuleRepository.save(rule);
        workingDayService.invalidateCache();
        return saved;
    }

    // ---- holidays ----
    @Transactional
    public Holiday addHoliday(Long calendarId, String name, LocalDate date, String type,
                              boolean halfDay, String description, String createdBy) {
        Holiday h = new Holiday();
        h.setCalendarId(calendarId);
        h.setHolidayName(name);
        h.setHolidayDate(date);
        h.setHolidayType(type == null ? "FIXED" : type.trim().toUpperCase());
        h.setHalfDay(halfDay);
        h.setDescription(description);
        h.setCreatedBy(createdBy);
        h.setCreatedAt(java.time.LocalDateTime.now());
        h.setUpdatedAt(java.time.LocalDateTime.now());
        Holiday saved = holidayRepository.save(h);
        workingDayService.invalidateCache();
        return saved;
    }

    @Transactional
    public Map<String, Object> importHolidaysCsv(Long calendarId, String csv, String createdBy) {
        // Format: name,date,type,half_day,description   (date = yyyy-MM-dd; half_day = true/false)
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_DATE;
        List<Map<String, Object>> rowErrors = new ArrayList<>();
        int imported = 0, skipped = 0;
        if (csv != null) {
            String[] lines = csv.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                if (i == 0 && line.toLowerCase().startsWith("name,")) continue; // header
                String[] cols = line.split(",", -1);
                if (cols.length < 3) {
                    rowErrors.add(Map.of("row", i + 1, "error", "expected at least name,date,type"));
                    continue;
                }
                try {
                    String name = cols[0].trim();
                    LocalDate date = LocalDate.parse(cols[1].trim(), f);
                    String type = cols[2].trim().toUpperCase();
                    boolean half = cols.length > 3 && cols[3].trim().equalsIgnoreCase("true");
                    String desc = cols.length > 4 ? cols[4].trim() : null;
                    String finalName = name;
                    boolean exists = holidayRepository
                            .findByCalendarIdAndHolidayDate(calendarId, date).stream()
                            .anyMatch(x -> x.getHolidayName() != null && x.getHolidayName().equalsIgnoreCase(finalName));
                    if (exists) { skipped++; continue; }
                    addHoliday(calendarId, name, date, type, half, desc, createdBy);
                    imported++;
                } catch (Exception e) {
                    rowErrors.add(Map.of("row", i + 1, "error", e.getMessage()));
                }
            }
        }
        workingDayService.invalidateCache();
        Map<String, Object> result = new HashMap<>();
        result.put("imported", imported);
        result.put("skipped_existing", skipped);
        result.put("errors", rowErrors);
        return result;
    }

    /** Clone all holidays of one year into the next year (same month/day), skipping duplicates. Returns count copied. */
    @Transactional
    public int copyHolidaysForward(Long calendarId, int fromYear) {
        int copied = 0;
        List<Holiday> src = holidayRepository.findByCalendarIdAndHolidayDateBetween(
                calendarId, LocalDate.of(fromYear, 1, 1), LocalDate.of(fromYear, 12, 31));
        for (Holiday h : src) {
            LocalDate target;
            try {
                target = h.getHolidayDate().withYear(fromYear + 1); // Feb-29 → Feb-28 in non-leap years
            } catch (Exception e) {
                target = LocalDate.of(fromYear + 1, 2, 28);
            }
            boolean exists = holidayRepository.findByCalendarIdAndHolidayDate(calendarId, target).stream()
                    .anyMatch(x -> x.getHolidayName().equalsIgnoreCase(h.getHolidayName()));
            if (exists) continue;
            Holiday copy = new Holiday();
            copy.setCalendarId(calendarId);
            copy.setHolidayName(h.getHolidayName());
            copy.setHolidayDate(target);
            copy.setHolidayType(h.getHolidayType());
            copy.setHalfDay(h.isHalfDay());
            copy.setDescription(h.getDescription());
            copy.setCreatedBy("copy-forward");
            copy.setCreatedAt(java.time.LocalDateTime.now());
            copy.setUpdatedAt(java.time.LocalDateTime.now());
            holidayRepository.save(copy);
            copied++;
        }
        workingDayService.invalidateCache();
        logger.info("Copied {} holidays from {} to {} in calendar {}", copied, fromYear, fromYear + 1, calendarId);
        return copied;
    }

    // ---- overrides ----
    @Transactional
    public WorkingDayOverride addOverride(Long calendarId, LocalDate date, String type, String reason) {
        String t = (type == null ? "WORKING" : type.trim().toUpperCase());
        if (!t.equals("WORKING") && !t.equals("OFF")) throw new IllegalArgumentException("type must be WORKING or OFF");
        WorkingDayOverride o = new WorkingDayOverride();
        o.setCalendarId(calendarId);
        o.setOverrideDate(date);
        o.setOverrideType(t);
        o.setReason(reason);
        WorkingDayOverride saved = overrideRepository.save(o);
        workingDayService.invalidateCache();
        return saved;
    }

    // ---- optional quota ----
    @Transactional
    public OptionalHolidayQuota setQuota(Long calendarId, int year, int maxPerEmployee) {
        OptionalHolidayQuota q = quotaRepository.findByCalendarIdAndYear(calendarId, year)
                .orElseGet(OptionalHolidayQuota::new);
        q.setCalendarId(calendarId);
        q.setYear(year);
        q.setMaxOptionalPerEmployee(maxPerEmployee);
        return quotaRepository.save(q);
    }
}
