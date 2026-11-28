package com.mentis.hrms.controller.api;

import com.mentis.hrms.service.OptionalHolidayService;
import com.mentis.hrms.service.WorkingDayService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

/**
 * Employee self-service calendar (REQ-3-10).
 *   GET  /api/calendar/my?year=2026&month=11   → month view (weekly offs, holidays, optional choices, day types)
 *   POST /api/calendar/optional/{holidayId}/choose    → choose optional holiday (quota-enforced)
 *   POST /api/calendar/optional/{holidayId}/unchoose  → release choice
 */
@RestController
@RequestMapping("/api/calendar")
public class EmployeeCalendarController {

    @Autowired private WorkingDayService workingDayService;
    @Autowired private OptionalHolidayService optionalHolidayService;

    @GetMapping("/my")
    public ResponseEntity<?> myCalendar(@RequestParam int year, @RequestParam int month, HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        YearMonth ym = YearMonth.of(year, month);
        List<Map<String, Object>> days = new ArrayList<>();
        for (WorkingDayService.DayInfo di : workingDayService.getDayInfos(employeeId, ym.atDay(1), ym.atEndOfMonth())) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", di.date.toString());
            d.put("dayType", di.dayType.name());
            d.put("holidayName", di.holidayName);
            d.put("note", di.note);
            d.put("expectedHours", di.expectedHours);
            d.put("employed", di.employedOnDate);
            days.add(d);
        }
        return ResponseEntity.ok(Map.of("year", year, "month", month, "days", days));
    }

    @PostMapping("/optional/{holidayId}/choose")
    public ResponseEntity<?> choose(@PathVariable Long holidayId, HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        try {
            optionalHolidayService.choose(employeeId, holidayId);
            return ResponseEntity.ok(Map.of("status", "CHOSEN", "holidayId", holidayId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/optional/{holidayId}/unchoose")
    public ResponseEntity<?> unchoose(@PathVariable Long holidayId, HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        optionalHolidayService.unchoose(employeeId, holidayId);
        return ResponseEntity.ok(Map.of("status", "REJECTED", "holidayId", holidayId));
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
