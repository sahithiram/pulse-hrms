package com.mentis.hrms.controller.api;

import com.mentis.hrms.model.*;
import com.mentis.hrms.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * HR calendar administration API (REQ-3-09). Coarse HR gate is enforced by RoleInterceptorV2;
 * fine-grained grant/deny for /api/hr/calendars/** must be configured in the Sprint-2 permission matrix
 * (deny-by-default until granted). All endpoints return JSON.
 *
 *   GET    /api/hr/calendars
 *   POST   /api/hr/calendars                          {name, locationId?, isDefault?, validFrom?, validTo?}
 *   GET    /api/hr/calendars/{id}/week-off-rules
 *   POST   /api/hr/calendars/{id}/week-off-rules      {dayOfWeek: 7, pattern: "ALL"}
 *   POST   /api/hr/calendars/{id}/holidays            {name, date, type?, halfDay?, description?}
 *   POST   /api/hr/calendars/{id}/holidays/import-csv {csv: "..."}
 *   POST   /api/hr/calendars/{id}/holidays/copy-year  {fromYear: 2025}
 *   POST   /api/hr/calendars/{id}/overrides           {date, type: "WORKING"|"OFF", reason?}
 *   PUT    /api/hr/calendars/{id}/optional-quota      {year, maxPerEmployee}
 */
@RestController
@RequestMapping("/api/hr/calendars")
public class CalendarAdminController {

    @Autowired private CalendarAdminService adminService;

    @GetMapping
    public List<Map<String, Object>> calendars() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WorkCalendar c : adminService.listCalendars()) out.add(calendarJson(c));
        return out;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        WorkCalendar c = adminService.createCalendar(
                str(body.get("name")),
                body.get("locationId") == null ? null : Long.valueOf(body.get("locationId").toString()),
                Boolean.TRUE.equals(body.get("isDefault")),
                body.get("validFrom") == null ? null : LocalDate.parse(body.get("validFrom").toString()),
                body.get("validTo") == null ? null : LocalDate.parse(body.get("validTo").toString()));
        return calendarJson(c);
    }

    @GetMapping("/{id}/week-off-rules")
    public List<Map<String, Object>> rules(@PathVariable Long id) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WeeklyOffRule r : adminService.weeklyOffRules(id)) {
            out.add(Map.of("id", r.getId(), "dayOfWeek", r.getDayOfWeek(), "pattern", r.getPattern()));
        }
        return out;
    }

    @PostMapping("/{id}/week-off-rules")
    public Map<String, Object> addRule(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int dow = Integer.parseInt(body.getOrDefault("dayOfWeek", "7").toString());
        WeeklyOffRule r = adminService.addWeeklyOffRule(id, dow, str(body.get("pattern")));
        return Map.of("id", r.getId(), "dayOfWeek", r.getDayOfWeek(), "pattern", r.getPattern());
    }

    @PostMapping("/{id}/holidays")
    public Map<String, Object> addHoliday(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Holiday h = adminService.addHoliday(id,
                str(body.get("name")),
                LocalDate.parse(str(body.get("date"))),
                str(body.get("type")),
                Boolean.TRUE.equals(body.get("halfDay")),
                str(body.get("description")),
                str(body.get("createdBy")));
        return holidayJson(h);
    }

    @PostMapping("/{id}/holidays/import-csv")
    public Map<String, Object> importCsv(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return adminService.importHolidaysCsv(id, str(body.get("csv")), str(body.get("createdBy")));
    }

    @PostMapping("/{id}/holidays/copy-year")
    public Map<String, Object> copyYear(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        int fromYear = Integer.parseInt(str(body.get("fromYear")));
        int copied = adminService.copyHolidaysForward(id, fromYear);
        return Map.of("copied", copied, "fromYear", fromYear, "toYear", fromYear + 1);
    }

    @PostMapping("/{id}/overrides")
    public Map<String, Object> override(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        WorkingDayOverride o = adminService.addOverride(id,
                LocalDate.parse(str(body.get("date"))),
                str(body.get("type")),
                str(body.get("reason")));
        return Map.of("id", o.getId(), "date", o.getOverrideDate().toString(),
                "type", o.getOverrideType(), "reason", o.getReason() == null ? "" : o.getReason());
    }

    @PutMapping("/{id}/optional-quota")
    public Map<String, Object> quota(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        OptionalHolidayQuota q = adminService.setQuota(id,
                Integer.parseInt(str(body.get("year"))),
                Integer.parseInt(str(body.getOrDefault("maxPerEmployee", "2"))));
        return Map.of("calendarId", q.getCalendarId(), "year", q.getYear(),
                "maxPerEmployee", q.getMaxOptionalPerEmployee());
    }

    private static Map<String, Object> calendarJson(WorkCalendar c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("locationId", c.getLocationId());
        m.put("isDefault", c.isDefault());
        m.put("validFrom", c.getValidFrom() == null ? null : c.getValidFrom().toString());
        m.put("validTo", c.getValidTo() == null ? null : c.getValidTo().toString());
        return m;
    }

    private static Map<String, Object> holidayJson(Holiday h) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("name", h.getHolidayName());
        m.put("date", h.getHolidayDate().toString());
        m.put("type", h.getHolidayType());
        m.put("halfDay", h.isHalfDay());
        m.put("calendarId", h.getCalendarId());
        m.put("description", h.getDescription());
        return m;
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }
}
