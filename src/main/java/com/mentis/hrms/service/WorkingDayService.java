// UPDATED in Sprint 5 (complete file, ready to overwrite).
// Changes vs the Sprint-3 delivery: DayInfo.shiftRef is now POPULATED via ShiftResolver (roster → employee_shift);
// everything else byte-compatible. If ShiftResolver is absent (Sprint 5 not applied), shiftRef stays null — identical to Sprint 3.
package com.mentis.hrms.service;

import com.mentis.hrms.model.*;
import com.mentis.hrms.model.enums.DayType;
import com.mentis.hrms.repository.*;
import com.mentis.hrms.util.WeeklyOffPatternResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sprint 3 — the ONLY authoritative answer to "what is this day for this employee?" (REQ-3-06..08).
 * No other class may contain weekday/holiday/weekly-off logic; callers must come here.
 *
 * Precedence per date: NOT_EMPLOYED > override > holiday(HALF_DAY aware) > optional-holiday-taken > weekly-off > WORKING.
 * A holiday that lands on a weekly off is counted ONCE (as HOLIDAY).
 */
@Service
@Transactional(readOnly = true)
public class WorkingDayService {

    private static final Logger logger = LoggerFactory.getLogger(WorkingDayService.class);

    /** Compact, immutable result for one day. */
    public static class DayInfo {
        public final LocalDate date;
        public final DayType dayType;
        public final String holidayName;   // null unless HOLIDAY / HALF_DAY_HOLIDAY / OPTIONAL_HOLIDAY_TAKEN
        public final String note;          // e.g. override reason
        public final double expectedHours;
        public final String shiftRef;      // stub until Sprint 5 (SH-001 etc.)
        public final boolean employedOnDate;

        public DayInfo(LocalDate date, DayType dayType, String holidayName, String note,
                       double expectedHours, String shiftRef, boolean employedOnDate) {
            this.date = date;
            this.dayType = dayType;
            this.holidayName = holidayName;
            this.note = note;
            this.expectedHours = expectedHours;
            this.shiftRef = shiftRef;
            this.employedOnDate = employedOnDate;
        }

        public boolean isWorking() {
            return dayType == DayType.WORKING || dayType == DayType.HALF_DAY_HOLIDAY;
        }

        @Override
        public String toString() {
            return "DayInfo{" + date + " " + dayType + (holidayName != null ? " (" + holidayName + ")" : "") + '}';
        }
    }

    @Autowired(required = false)
    private ShiftResolver shiftResolver;   // Sprint 5 — populates shiftRef when shifts are configured

    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private WorkCalendarRepository workCalendarRepository;
    @Autowired private WeeklyOffRuleRepository weeklyOffRuleRepository;
    @Autowired private HolidayCalendarRepository holidayCalendarRepository;
    @Autowired private WorkingDayOverrideRepository workingDayOverrideRepository;
    @Autowired private OptionalHolidayChoiceRepository optionalHolidayChoiceRepository;

    @Value("${hrms.working.hours.full-day:8.0}")
    private double fullDayHours;

    @Value("${hrms.calendar.cache-ttl-seconds:60}")
    private long cacheTtlSeconds;

    private final ConcurrentHashMap<Long, CalendarBundle> bundleCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> bundleLoadedAt = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------- bundle

    private static final class CalendarBundle {
        final WorkCalendar calendar;
        final List<WeeklyOffRule> weeklyOffRules;
        final Map<LocalDate, WorkingDayOverride> overridesByDate;

        CalendarBundle(WorkCalendar calendar, List<WeeklyOffRule> rules, List<WorkingDayOverride> overrides) {
            this.calendar = calendar;
            this.weeklyOffRules = rules;
            this.overridesByDate = overrides.stream()
                    .collect(Collectors.toMap(WorkingDayOverride::getOverrideDate, o -> o, (a, b) -> a));
        }
    }

    /** Call after ANY holiday/rule/override/quota/calendar change (wired into CalendarAdminService). */
    public void invalidateCache() {
        bundleCache.clear();
        bundleLoadedAt.clear();
        logger.info("Working-day calendar cache invalidated");
    }

    private CalendarBundle bundle(WorkCalendar calendar) {
        long now = System.currentTimeMillis();
        Long loaded = bundleLoadedAt.get(calendar.getId());
        if (loaded != null && (now - loaded) < cacheTtlSeconds * 1000L) {
            CalendarBundle cached = bundleCache.get(calendar.getId());
            if (cached != null) return cached;
        }
        // Range-load overrides for a 400-day window around today (covers all normal uses)
        LocalDate from = LocalDate.now().minusDays(200);
        LocalDate to = LocalDate.now().plusDays(400);
        CalendarBundle b = new CalendarBundle(
                calendar,
                weeklyOffRuleRepository.findByCalendarId(calendar.getId()),
                workingDayOverrideRepository.findByCalendarIdAndOverrideDateBetween(calendar.getId(), from, to));
        bundleCache.put(calendar.getId(), b);
        bundleLoadedAt.put(calendar.getId(), now);
        return b;
    }

    // ------------------------------------------------------------ resolution

    private WorkCalendar resolveCalendar(Employee employee, LocalDate onDate) {
        Long loc = employee.getLocationId();
        if (loc != null) {
            Optional<WorkCalendar> byLoc = workCalendarRepository.findByLocationId(loc).stream()
                    .filter(c -> c.isValidOn(onDate))
                    .findFirst();
            if (byLoc.isPresent()) return byLoc.get();
        }
        Optional<WorkCalendar> def = workCalendarRepository.findFirstByIsDefaultTrue();
        if (def.isPresent()) return def.get();
        throw new IllegalStateException(
                "No default work_calendar found. Run V5 migration and seed the default calendar.");
    }

    private boolean employedOn(Employee employee, LocalDate date) {
        if (employee.getStartDate() != null && date.isBefore(employee.getStartDate())) return false;
        try {
            java.time.LocalDate exit = employee.getExitDate(); // Sprint 1 lifecycle field
            if (exit != null && date.isAfter(exit)) return false;
        } catch (Throwable ignored) {
            // Sprint 1 not applied yet — fall back to start-date only
        }
        String lifecycle = null;
        try {
            lifecycle = employee.getEmploymentStatus();
            if ("EXITED".equalsIgnoreCase(lifecycle) && employee.getExitDate() != null
                    && date.isAfter(employee.getExitDate())) {
                return false;
            }
        } catch (Throwable ignored) {
            // pre-Sprint-1 Employee — ignore
        }
        return true;
    }

    // ------------------------------------------------------------- public API

    public DayInfo getDayInfo(String employeeId, LocalDate date) {
        return getDayInfos(employeeId, date, date).get(0);
    }

    public List<DayInfo> getDayInfos(String employeeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) throw new IllegalArgumentException("to must be on/after from");
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 3700) throw new IllegalArgumentException("Range too large (>10 years)");

        Optional<Employee> empOpt = employeeRepository.findByEmployeeId(employeeId);
        if (empOpt.isEmpty()) {
            List<DayInfo> out = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                out.add(new DayInfo(d, DayType.NOT_EMPLOYED, null, "employee_not_found", 0, null, false));
            }
            return out;
        }
        Employee employee = empOpt.get();
        WorkCalendar calendar = resolveCalendar(employee, from);
        CalendarBundle b = bundle(calendar);

        // ONE holiday query per call (no per-day queries) — REQ-3-07
        Map<LocalDate, Holiday> holidaysByDate = holidayCalendarRepository
                .findByCalendarIdAndHolidayDateBetween(calendar.getId(), from, to)
                .stream()
                .collect(Collectors.toMap(Holiday::getHolidayDate, h -> h, (a1, a2) -> a1));

        // Optional-holiday choices for this employee, intersected with the holidays in range
        List<OptionalHolidayChoice> choices = optionalHolidayChoiceRepository.findByEmployeeId(employeeId);
        Set<Long> optionalChosenIds = choices.stream()
                .filter(c -> "CHOSEN".equalsIgnoreCase(c.getStatus()))
                .map(OptionalHolidayChoice::getHolidayId)
                .collect(Collectors.toSet());

        List<DayInfo> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String shiftRef = null;
            if (shiftResolver != null) {
                try {
                    shiftRef = shiftResolver.resolve(employeeId, d).map(sh -> "SHIFT:" + sh.getId() + ":" + sh.getName()).orElse(null);
                } catch (Exception e) {
                    logger.debug("Shift resolve failed for {} on {}: {}", employeeId, d, e.getMessage());
                }
            }
            out.add(dayInfoInternal(d, employee, b, holidaysByDate, optionalChosenIds, shiftRef));
        }
        return out;
    }

    public List<LocalDate> getWorkingDays(String employeeId, LocalDate from, LocalDate to) {
        return getDayInfos(employeeId, from, to).stream()
                .filter(DayInfo::isWorking)
                .map(di -> di.date)
                .collect(Collectors.toList());
    }

    public long countWorkingDays(String employeeId, LocalDate from, LocalDate to) {
        return getDayInfos(employeeId, from, to).stream().filter(DayInfo::isWorking).count();
    }

    public double countWorkingHours(String employeeId, LocalDate from, LocalDate to) {
        return getDayInfos(employeeId, from, to).stream().mapToDouble(di -> di.expectedHours).sum();
    }

    // ------------------------------------------------------------- internals

    private DayInfo dayInfoInternal(LocalDate date, Employee employee, CalendarBundle b,
                                    Map<LocalDate, Holiday> holidaysByDate, Set<Long> optionalChosenIds,
                                    String shiftRef) {
        double full = fullDayHours;
        boolean employed = employedOn(employee, date);
        if (!employed) {
            return new DayInfo(date, DayType.NOT_EMPLOYED, null, "not_employed_on_date", 0, shiftRef, false);
        }

        // 1) Override beats everything else
        WorkingDayOverride ov = b.overridesByDate.get(date);
        if (ov != null) {
            if ("WORKING".equalsIgnoreCase(ov.getOverrideType())) {
                return new DayInfo(date, DayType.WORKING, null, ov.getReason(), full, shiftRef, true);
            }
            // OFF override → behaves as a company off day (counted once, like a weekly off)
            return new DayInfo(date, DayType.WEEKLY_OFF, null, ov.getReason(), 0, shiftRef, true);
        }

        // 2) Holiday on this calendar
        Holiday h = holidaysByDate.get(date);
        if (h != null) {
            boolean optional = isOptional(h);
            if (!optional) {
                if (h.isHalfDay()) {
                    return new DayInfo(date, DayType.HALF_DAY_HOLIDAY, h.getHolidayName(),
                            "half_day_holiday", full / 2.0, shiftRef, true);
                }
                // A holiday landing on a weekly off counts ONCE — stays HOLIDAY
                return new DayInfo(date, DayType.HOLIDAY, h.getHolidayName(), null, 0, shiftRef, true);
            }
            // 3) Optional holiday: only affects THIS employee if they chose it
            if (optionalChosenIds.contains(h.getId())) {
                if (h.isHalfDay()) {
                    return new DayInfo(date, DayType.HALF_DAY_HOLIDAY, h.getHolidayName(),
                            "optional_half_day_chosen", full / 2.0, shiftRef, true);
                }
                return new DayInfo(date, DayType.OPTIONAL_HOLIDAY_TAKEN, h.getHolidayName(),
                        "optional_holiday_chosen", 0, shiftRef, true);
            }
            // Not chosen → behaves as a normal working day (falls through)
        }

        // 4) Weekly-off rules (pattern-aware)
        for (WeeklyOffRule rule : b.weeklyOffRules) {
            if (rule.getDayOfWeek() != null && rule.getDayOfWeek() == date.getDayOfWeek().getValue()
                    && WeeklyOffPatternResolver.matches(rule.getPattern(), date)) {
                return new DayInfo(date, DayType.WEEKLY_OFF, null, null, 0, shiftRef, true);
            }
        }

        // 5) Default: working day
        return new DayInfo(date, DayType.WORKING, null, null, full, shiftRef, true);
    }

    private boolean isOptional(Holiday h) {
        String t = h.getHolidayType();
        return t != null && t.equalsIgnoreCase("OPTIONAL");
    }
}
