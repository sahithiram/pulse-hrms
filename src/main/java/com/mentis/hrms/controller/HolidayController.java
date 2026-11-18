package com.mentis.hrms.controller;

import com.mentis.hrms.dto.HolidayDTO;
import com.mentis.hrms.model.Holiday;
import com.mentis.hrms.service.HolidayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/api/holidays")
public class HolidayController {

    @Autowired
    private HolidayService holidayService;

    // ========== API ENDPOINTS FOR AJAX CALLS ==========

    // Get all holidays
    @GetMapping("/all")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAllHolidays() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Holiday> holidays = holidayService.getAllHolidays();

            List<HolidayDTO> holidayDTOs = holidays.stream()
                    .map(h -> new HolidayDTO(
                            h.getId(),
                            h.getHolidayName(),
                            h.getHolidayDate().toString(),
                            h.getHolidayType(),
                            h.getDescription()
                    ))
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("holidays", holidayDTOs);
            response.put("count", holidayDTOs.size());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // Get holidays by month
    @GetMapping("/month/{year}/{month}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHolidaysByMonth(
            @PathVariable int year,
            @PathVariable int month) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Holiday> holidays = holidayService.getHolidaysByMonth(year, month);

            Map<String, List<HolidayDTO>> holidaysByDate = new HashMap<>();

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Holiday h : holidays) {
                String dateStr = h.getHolidayDate().format(formatter);

                if (!holidaysByDate.containsKey(dateStr)) {
                    holidaysByDate.put(dateStr, new ArrayList<>());
                }

                holidaysByDate.get(dateStr).add(new HolidayDTO(
                        h.getId(),
                        h.getHolidayName(),
                        dateStr,
                        h.getHolidayType(),
                        h.getDescription()
                ));
            }

            response.put("success", true);
            response.put("holidays", holidaysByDate);
            response.put("count", holidays.size());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // Get holidays for a specific date
    @GetMapping("/date")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getHolidaysByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Holiday> holidays = holidayService.getHolidaysByDate(date);

            List<HolidayDTO> holidayDTOs = holidays.stream()
                    .map(h -> new HolidayDTO(
                            h.getId(),
                            h.getHolidayName(),
                            date.toString(),
                            h.getHolidayType(),
                            h.getDescription()
                    ))
                    .collect(Collectors.toList());

            response.put("success", true);
            response.put("holidays", holidayDTOs);
            response.put("count", holidayDTOs.size());
            response.put("date", date.toString());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createHoliday(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            // Accept BOTH naming conventions (frontend may send name/holidayName)
            String holidayName = firstNonBlank(request.get("holidayName"), request.get("name"));
            String holidayDateStr = firstNonBlank(request.get("holidayDate"), request.get("date"));
            String holidayType = firstNonBlank(request.get("holidayType"), request.get("type"));
            String description = request.get("description");
            String createdBy = firstNonBlank(request.get("createdBy"), "HR Manager");

            if (holidayName == null || holidayName.isBlank()) {
                response.put("success", false);
                response.put("error", "Holiday name is required");
                return ResponseEntity.badRequest().body(response);
            }
            if (holidayDateStr == null || holidayDateStr.isBlank()) {
                response.put("success", false);
                response.put("error", "Holiday date is required");
                return ResponseEntity.badRequest().body(response);
            }

            LocalDate holidayDate;
            try {
                holidayDate = LocalDate.parse(holidayDateStr);
            } catch (Exception ex) {
                response.put("success", false);
                response.put("error", "Invalid date format: " + holidayDateStr);
                return ResponseEntity.badRequest().body(response);
            }

            Holiday holiday = new Holiday();
            holiday.setHolidayName(holidayName);
            holiday.setHolidayDate(holidayDate);
            holiday.setHolidayType(holidayType != null ? holidayType : "public");
            holiday.setDescription(description);
            holiday.setCreatedBy(createdBy);

            Holiday savedHoliday = holidayService.createHoliday(holiday);

            response.put("success", true);
            response.put("message", "Holiday created successfully");
            response.put("holidayId", savedHoliday.getId());
            response.put("holiday", new HolidayDTO(
                    savedHoliday.getId(),
                    savedHoliday.getHolidayName(),
                    savedHoliday.getHolidayDate().toString(),
                    savedHoliday.getHolidayType(),
                    savedHoliday.getDescription()
            ));
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // Add this private helper INSIDE HolidayController class
    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return null;
    }

    // Delete holiday
    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteHoliday(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean deleted = holidayService.deleteHoliday(id);
            response.put("success", deleted);
            response.put("message", deleted ? "Holiday deleted successfully" : "Holiday not found");
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // Get upcoming holidays
    @GetMapping("/upcoming")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUpcomingHolidays() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Holiday> holidays = holidayService.getUpcomingHolidays();

            List<Map<String, Object>> upcomingList = new ArrayList<>();
            LocalDate today = LocalDate.now();

            for (Holiday h : holidays) {
                Map<String, Object> item = new HashMap<>();
                item.put("date", h.getHolidayDate().toString());
                item.put("name", h.getHolidayName());
                item.put("type", h.getHolidayType());
                item.put("description", h.getDescription());

                // Calculate days away
                long daysAway = java.time.temporal.ChronoUnit.DAYS.between(today, h.getHolidayDate());
                item.put("daysAway", daysAway);

                upcomingList.add(item);
            }

            response.put("success", true);
            response.put("upcoming", upcomingList);
            response.put("count", upcomingList.size());
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    // ========== PAGE VIEW ENDPOINTS ==========

    @GetMapping("/hr-calendar")
    public String hrCalendarView() {
        return "dashboard/hr/holiday-calendar";
    }
}