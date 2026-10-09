package com.mentis.hrms.EmployeePerformance.controller;

import com.mentis.hrms.EmployeePerformance.model.AdminEmpPerformance;
import com.mentis.hrms.EmployeePerformance.service.AdminEmpPerformanceService;
import com.mentis.hrms.model.Attendance;
import com.mentis.hrms.model.Employee;
import com.mentis.hrms.repository.AttendanceRepository;
import com.mentis.hrms.repository.EmployeeRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dashboard/hr/performance")
public class AdminEmpPerformanceController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEmpPerformanceController.class);

    @Autowired
    private AdminEmpPerformanceService performanceService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    // ============================================================
    // VIEW PAGES
    // ============================================================

    @GetMapping
    public String performancePage(Model model, HttpSession session) {
        logger.info("Loading Performance Management page");

        if (session.getAttribute("userId") == null) {
            return "redirect:/candidate/auth/login";
        }

        String role = (String) session.getAttribute("userRole");
        if (!"SUPER_ADMIN".equals(role) && !"HR".equals(role)) {
            return "redirect:/candidate/dashboard";
        }

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        String currentMonthStr = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        List<Employee> employees = employeeRepository.findAll();

        model.addAttribute("employees", employees);
        model.addAttribute("currentMonth", currentMonthStr);
        model.addAttribute("isSuperAdmin", "SUPER_ADMIN".equals(role));
        model.addAttribute("userName", session.getAttribute("userName"));

        return "EmployeePerformance/performance";
    }

    // ============================================================
    // REST API ENDPOINTS
    // ============================================================

    // ===== GET ALL EMPLOYEES FOR DROPDOWN =====
    @GetMapping("/api/employees/all")
    @ResponseBody
    public ResponseEntity<?> getAllEmployees() {
        try {
            logger.info("Fetching all employees for performance dropdown");

            List<Employee> employees = employeeRepository.findAll();

            // Filter only active employees
            List<Map<String, Object>> employeeList = employees.stream()
                    .filter(emp -> emp.getIsActive() != null && emp.getIsActive())
                    .map(emp -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("employeeId", emp.getEmployeeId());
                        map.put("firstName", emp.getFirstName() != null ? emp.getFirstName() : "");
                        map.put("lastName", emp.getLastName() != null ? emp.getLastName() : "");
                        map.put("department", emp.getDepartment() != null ? emp.getDepartment() : "N/A");
                        map.put("designation", emp.getDesignation() != null ? emp.getDesignation() : "");
                        return map;
                    })
                    .collect(Collectors.toList());

            logger.info("Found {} active employees", employeeList.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("employees", employeeList);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching employees: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ===== CALCULATE PERFORMANCE =====
    @PostMapping("/api/calculate")
    @ResponseBody
    public ResponseEntity<?> calculatePerformance(
            @RequestParam String employeeId,
            @RequestParam String month,
            @RequestParam(required = false) Double behaviorScore,
            @RequestParam(required = false) String behaviorRemarks,
            @RequestParam(required = false) Integer behaviorStars,
            @RequestParam(required = false) Double initiativeScore,
            HttpSession session) {

        try {
            logger.info("Calculating performance for {} in {}", employeeId, month);

            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int monthNum = Integer.parseInt(parts[1]);

            String reviewedBy = (String) session.getAttribute("userName");
            if (reviewedBy == null) reviewedBy = "Super Admin";

            if (behaviorStars == null) behaviorStars = 3;
            if (behaviorScore == null) behaviorScore = (double) (behaviorStars * 20);
            if (initiativeScore == null) initiativeScore = 50.0;

            AdminEmpPerformance performance = performanceService.calculateAndSavePerformance(
                    employeeId, year, monthNum,
                    behaviorScore, behaviorRemarks, behaviorStars,
                    initiativeScore,
                    reviewedBy
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Performance calculated successfully");
            response.put("performance", performance);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error calculating performance: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ===== GET PERFORMANCES LIST =====
    @GetMapping("/api/list")
    @ResponseBody
    public ResponseEntity<?> getPerformances(
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String department) {

        try {
            LocalDate reviewMonth;
            if (month == null || month.isEmpty()) {
                reviewMonth = LocalDate.now().withDayOfMonth(1);
            } else {
                String[] parts = month.split("-");
                int year = Integer.parseInt(parts[0]);
                int monthNum = Integer.parseInt(parts[1]);
                reviewMonth = LocalDate.of(year, monthNum, 1);
            }

            List<AdminEmpPerformance> performances = performanceService.getPerformancesForMonth(
                    reviewMonth.getYear(), reviewMonth.getMonthValue());

            final String deptFilter = department;
            if (deptFilter != null && !deptFilter.isEmpty() && !"All".equals(deptFilter)) {
                performances = performances.stream()
                        .filter(p -> deptFilter.equals(p.getDepartment()))
                        .toList();
            }

            Map<String, Object> summary = performanceService.getPerformanceSummary(
                    reviewMonth.getYear(), reviewMonth.getMonthValue());

            List<LocalDate> availableMonths = performanceService.getAvailableMonths();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("performances", performances);
            response.put("summary", summary);
            response.put("availableMonths", availableMonths);
            response.put("month", reviewMonth.toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching performances: {}", e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ===== GET EMPLOYEE HISTORY =====
    @GetMapping("/api/history/{employeeId}")
    @ResponseBody
    public ResponseEntity<?> getEmployeeHistory(@PathVariable String employeeId) {
        try {
            List<AdminEmpPerformance> history = performanceService.getEmployeePerformanceHistory(employeeId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("history", history);
            response.put("employeeId", employeeId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ===== GET ATTENDANCE PREVIEW =====
    @GetMapping("/api/attendance-preview/{employeeId}/{month}")
    @ResponseBody
    public ResponseEntity<?> getAttendancePreview(
            @PathVariable String employeeId,
            @PathVariable String month) {

        try {
            logger.info("Fetching attendance preview for employee {} in month {}", employeeId, month);

            String[] parts = month.split("-");
            int year = Integer.parseInt(parts[0]);
            int monthNum = Integer.parseInt(parts[1]);

            AdminEmpPerformanceService.AttendanceStats stats =
                    performanceService.getAttendanceStats(employeeId, year, monthNum);

            logger.info("Attendance preview for {}: Score={}%, Present={}, Absent={}, Total={}",
                    employeeId, stats.attendancePercentage, stats.presentDays, stats.absentDays, stats.totalWorkingDays);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("attendanceScore", stats.attendancePercentage);
            response.put("totalWorkingDays", stats.totalWorkingDays);
            response.put("presentDays", stats.presentDays);
            response.put("absentDays", stats.absentDays);
            response.put("month", month);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error fetching attendance preview for {}: {}", employeeId, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ===== DELETE PERFORMANCE =====
    @DeleteMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> deletePerformance(@PathVariable Long id) {
        try {
            performanceService.deletePerformance(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Performance record deleted");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // ===== GET AVAILABLE MONTHS =====
    @GetMapping("/api/months")
    @ResponseBody
    public ResponseEntity<?> getAvailableMonths() {
        try {
            List<LocalDate> months = performanceService.getAvailableMonths();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("months", months);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
