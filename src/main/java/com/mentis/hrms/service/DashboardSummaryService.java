package com.mentis.hrms.service;

import com.mentis.hrms.repository.EmployeeDailyMetricsRepository;
import com.mentis.hrms.model.EmployeeDailyMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REQ-6-09 — HR dashboard reads SUMMARY rows (employee_daily_metrics), never raw punches.
 * Freshness is always stamped so screens can show "as of" and empty/error states.
 */
@Service
public class DashboardSummaryService {

    @Autowired
    private EmployeeDailyMetricsRepository metricsRepository;

    public Map<String, Object> attendanceExceptions(LocalDate date) {
        List<EmployeeDailyMetrics> rows = metricsRepository.findByMetricsDate(date);
        Map<String, List<EmployeeDailyMetrics>> byStatus = rows.stream()
                .collect(Collectors.groupingBy(EmployeeDailyMetrics::getStatus));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", date.toString());
        out.put("freshness", rows.stream().map(EmployeeDailyMetrics::getComputedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).map(Object::toString).orElse("no rows"));
        out.put("total", rows.size());
        out.put("absent", ids(byStatus.get("ABSENT")));
        out.put("incomplete", ids(byStatus.get("INCOMPLETE")));
        out.put("halfDay", ids(byStatus.get("HALF_DAY")));
        out.put("onLeave", ids(byStatus.get("ON_LEAVE")));
        out.put("late", rows.stream().filter(m -> m.getLateMinutes() > 0)
                .map(EmployeeDailyMetrics::getEmployeeId).collect(Collectors.toList()));
        return out;
    }

    private static List<String> ids(List<EmployeeDailyMetrics> rows) {
        return rows == null ? List.of() : rows.stream().map(EmployeeDailyMetrics::getEmployeeId).collect(Collectors.toList());
    }
}
