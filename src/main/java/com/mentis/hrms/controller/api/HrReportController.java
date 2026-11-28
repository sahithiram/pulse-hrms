package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.SavedReportView;
import com.mentis.hrms.repository.AttendanceRegisterReadRepository;
import com.mentis.hrms.repository.SavedReportViewRepository;
import com.mentis.hrms.service.LeaveLiabilityReportService;
import com.mentis.hrms.service.WorkforceSummaryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 *   GET  /api/reports/headcount
 *   GET  /api/reports/attendance-register?from=2026-11-01&to=2026-11-26   (max 31 days)
 *   GET  /api/reports/leave-liability
 *   GET  /api/reports/saved-views?reportKey=attendance-register
 *   POST /api/reports/saved-views          {reportKey, name, filtersJson}
 *   DELETE /api/reports/saved-views/{id}
 * Export (Excel/PDF) is REQ-6-11 and intentionally NOT faked here — see 08_REPORTS_DATA_SOURCES.md.
 * EXPORT permission + audit row are REQUIRED when export is implemented; session-only is insufficient.
 */
@RestController
@RequestMapping("/api/reports")
public class HrReportController {

    @Autowired private WorkforceSummaryService workforceSummaryService;
    @Autowired private AttendanceRegisterReadRepository registerRepository;
    @Autowired private LeaveLiabilityReportService leaveLiabilityReportService;
    @Autowired private SavedReportViewRepository savedViewRepository;

    @GetMapping("/headcount")
    public ResponseEntity<?> headcount(HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(workforceSummaryService.headcountSummary());
    }

    @GetMapping("/attendance-register")
    public ResponseEntity<?> attendanceRegister(@RequestParam String from, @RequestParam String to, HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        if (t.isBefore(f)) return ResponseEntity.status(400).body(Map.of("error", "INVALID_RANGE"));
        if (java.time.temporal.ChronoUnit.DAYS.between(f, t) > 31) {
            return ResponseEntity.status(400).body(Map.of("error", "RANGE_TOO_LARGE", "maxDays", 31));
        }
        List<Map<String, Object>> rows = registerRepository.register(f, t).stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("employeeId", r[0]);
            m.put("name", r[1]);
            m.put("date", String.valueOf(r[2]));
            m.put("status", r[3]);
            m.put("netMinutes", r[4]);
            m.put("lateMinutes", r[5]);
            m.put("overtimeMinutes", r[6]);
            m.put("regularized", r[7]);
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("from", f.toString());
        report.put("to", t.toString());
        report.put("rows", rows);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/leave-liability")
    public ResponseEntity<?> leaveLiability(HttpSession session) {
        if (sessionEmployee(session) == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(leaveLiabilityReportService.liability());
    }

    @GetMapping("/saved-views")
    public ResponseEntity<?> savedViews(@RequestParam String reportKey, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        return ResponseEntity.ok(savedViewRepository.findByUserIdAndReportKeyOrderByNameAsc(u, reportKey)
                .stream().map(this::viewJson).collect(Collectors.toList()));
    }

    @PostMapping("/saved-views")
    public ResponseEntity<?> saveView(@RequestBody Map<String, String> body, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        String reportKey = body.get("reportKey"), name = body.get("name"), filters = body.get("filtersJson");
        if (reportKey == null || name == null || filters == null || name.isBlank()) {
            return ResponseEntity.status(400).body(Map.of("error", "MISSING_FIELDS"));
        }
        SavedReportView view = savedViewRepository
                .findByUserIdAndReportKeyAndName(u, reportKey, name)
                .orElseGet(SavedReportView::new);
        view.setUserId(u);
        view.setReportKey(reportKey);
        view.setName(name);
        view.setFiltersJson(filters);
        return ResponseEntity.ok(Map.of("status", "SAVED", "id", savedViewRepository.save(view).getId()));
    }

    @DeleteMapping("/saved-views/{id}")
    public ResponseEntity<?> deleteView(@PathVariable Long id, HttpSession session) {
        String u = sessionEmployee(session);
        if (u == null) return ResponseEntity.status(401).body(Map.of("error", "UNAUTHENTICATED"));
        savedViewRepository.deleteByIdAndUserId(id, u);
        return ResponseEntity.ok(Map.of("status", "DELETED"));
    }

    private Map<String, Object> viewJson(SavedReportView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("reportKey", v.getReportKey());
        m.put("name", v.getName());
        m.put("filtersJson", v.getFiltersJson());
        return m;
    }

    private static String sessionEmployee(HttpSession session) {
        if (session == null) return null;
        Object u = session.getAttribute("userId");
        return u == null ? null : u.toString();
    }
}
