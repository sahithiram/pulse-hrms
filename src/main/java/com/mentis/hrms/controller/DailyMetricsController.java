package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.EmployeeDailyMetrics;
import com.mentis.hrms.repository.EmployeeDailyMetricsRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 *   GET /api/metrics/my?year=2026&month=11   → my daily metrics for the month
 *   GET /api/metrics/day?date=2026-11-26     → all rows for a date (HR/dashboard usage)
 */
@RestController
@RequestMapping("/api/metrics")
public class DailyMetricsController {

    @Autowired
    private EmployeeDailyMetricsRepository metricsRepository;

    @GetMapping("/my")
    public ResponseEntity<?> my(@RequestParam int year, @RequestParam int month, HttpSession session) {
        String employeeId = sessionEmployee(session);
        if (employeeId == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        YearMonth ym = YearMonth.of(year, month);
        List<Map<String, Object>> rows = metricsRepository
                .findByEmployeeIdAndMetricsDateBetweenOrderByMetricsDateAsc(employeeId, ym.atDay(1), ym.atEndOfMonth())
                .stream().map(this::toJson).collect(Collectors.toList());
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/day")
    public ResponseEntity<?> day(@RequestParam String date, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        List<Map<String, Object>> rows = metricsRepository.findByMetricsDate(LocalDate.parse(date))
                .stream().map(this::toJson).collect(Collectors.toList());
        return ResponseEntity.ok(rows);
    }

    private Map<String, Object> toJson(EmployeeDailyMetrics m) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("employeeId", m.getEmployeeId());
        d.put("date", m.getMetricsDate().toString());
        d.put("dayType", m.getDayType());
        d.put("shiftId", m.getShiftId());
        d.put("status", m.getStatus());
        d.put("firstIn", m.getFirstIn() == null ? null : m.getFirstIn().toString());
        d.put("lastOut", m.getLastOut() == null ? null : m.getLastOut().toString());
        d.put("grossMinutes", m.getGrossMinutes());
        d.put("breakMinutes", m.getBreakMinutes());
        d.put("netMinutes", m.getNetMinutes());
        d.put("expectedMinutes", m.getExpectedMinutes());
        d.put("lateMinutes", m.getLateMinutes());
        d.put("earlyOutMinutes", m.getEarlyOutMinutes());
        d.put("overtimeMinutes", m.getOvertimeMinutes());
        d.put("leaveFraction", m.getLeaveFraction());
        d.put("isRegularized", m.isRegularized());
        d.put("ruleVersion", m.getRuleVersion());
        return d;
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
