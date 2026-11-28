package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.EmployeeDailyMetrics;
import com.mentis.hrms.repository.EmployeeDailyMetricsRepository;
import com.mentis.hrms.service.DashboardSummaryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 *   GET /api/dashboard/hr/attendance-exceptions?date=2026-11-26
 *   GET /api/dashboard/employee/my-day                      (today's metrics for the session employee)
 * References the metrics summary table only — never raw punches (REQ-6-09).
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardSummaryController {

    @Autowired private DashboardSummaryService dashboardSummaryService;
    @Autowired private EmployeeDailyMetricsRepository metricsRepository;

    @GetMapping("/hr/attendance-exceptions")
    public ResponseEntity<?> attendanceExceptions(@RequestParam(required = false) String date, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        LocalDate d = date != null ? LocalDate.parse(date) : LocalDate.now();
        return ResponseEntity.ok(dashboardSummaryService.attendanceExceptions(d));
    }

    @GetMapping("/employee/my-day")
    public ResponseEntity<?> myDay(HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        LocalDate today = LocalDate.now();
        List<EmployeeDailyMetrics> own = metricsRepository.findByEmployeeIdAndMetricsDateBetweenOrderByMetricsDateAsc(u, today, today);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        if (own.isEmpty()) {
            out.put("state", "EMPTY");
            out.put("message", "No metrics yet for today — check in to start your day.");
        } else {
            EmployeeDailyMetrics m = own.get(0);
            out.put("state", "OK");
            out.put("status", m.getStatus());
            out.put("firstIn", m.getFirstIn() == null ? null : m.getFirstIn().toString());
            out.put("lastOut", m.getLastOut() == null ? null : m.getLastOut().toString());
            out.put("netMinutes", m.getNetMinutes());
            out.put("lateMinutes", m.getLateMinutes());
            out.put("overtimeMinutes", m.getOvertimeMinutes());
            out.put("regularized", m.isRegularized());
            out.put("freshness", m.getComputedAt() == null ? null : m.getComputedAt().toString());
        }
        return ResponseEntity.ok(out);
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
