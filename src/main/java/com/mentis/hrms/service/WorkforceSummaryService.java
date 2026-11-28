package com.mentis.hrms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * REQ-6-10 subset implementable against VERIFIED schema: headcount + joiners this month + by department.
 * Attrition by month is NOT implemented — Employee has no exit/resignation date column; adding a fake
 * number would be worse than omitting it (see 08_REPORTS_DATA_SOURCES.md).
 */
@Service
public class WorkforceSummaryService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> headcountSummary() {
        Long active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE (is_active = TRUE OR (is_active IS NULL AND active = TRUE))",
                Long.class);
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        Long joiners = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE start_date >= ? AND start_date <= ?",
                Long.class, first, LocalDate.now());
        List<Map<String, Object>> byDept = jdbcTemplate.queryForList(
                "SELECT COALESCE(NULLIF(department, ''), 'UNASSIGNED') AS department, COUNT(*) AS headcount " +
                "FROM employees WHERE (is_active = TRUE OR (is_active IS NULL AND active = TRUE)) " +
                "GROUP BY COALESCE(NULLIF(department, ''), 'UNASSIGNED') ORDER BY headcount DESC");

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("freshness", LocalDate.now().toString());
        out.put("activeHeadcount", active);
        out.put("joinersThisMonth", joiners);
        out.put("byDepartment", byDept);
        out.put("attrition", "NOT_IMPLEMENTED — no exit-date source yet (see 08_REPORTS_DATA_SOURCES.md)");
        return out;
    }
}
