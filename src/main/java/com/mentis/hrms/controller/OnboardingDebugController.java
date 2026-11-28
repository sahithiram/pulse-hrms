package com.mentis.hrms.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.OnboardingDocument;
import com.mentis.hrms.service.DocumentService;
import com.mentis.hrms.service.EmployeeService;
import com.mentis.hrms.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sprint 8 (REQ-8-05) — debug/test endpoints split out of NewOnboardingController.
 * IMPORTANT: these are diagnostic endpoints carried over 1:1 (all URLs preserved).
 * They are NOT authenticated by this class; production exposure MUST be gated at the
 * security layer (see docs 05) or this controller should be excluded from the war.
 */
@Controller
@RequestMapping("/onboarding/debug")
public class OnboardingDebugController {
    private static final Logger logger = LoggerFactory.getLogger(OnboardingDebugController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private NotificationService notificationService;

    /* ---------- DEBUG ENDPOINTS ---------- */
    @GetMapping("/employees")
    @ResponseBody
    public List<Map<String, Object>> debugEmployees() {
        List<Employee> allEmployees = employeeService.getAllEmployees();
        return allEmployees.stream().map(emp -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", emp.getId());
            data.put("employeeId", emp.getEmployeeId());
            data.put("name", emp.getFirstName() + " " + emp.getLastName());
            data.put("onboardingStatus", emp.getOnboardingStatus());
            data.put("totalDocuments", emp.getTotalDocuments());
            data.put("submittedDocuments", emp.getSubmittedDocuments());
            data.put("verifiedDocuments", emp.getVerifiedDocuments());
            return data;
        }).collect(Collectors.toList());
    }

    @PostMapping("/create-test-employee")
    @ResponseBody
    public String createTestEmployee() {
        try {
            Employee testEmployee = new Employee();
            testEmployee.setEmployeeId("TEST" + System.currentTimeMillis());
            testEmployee.setFirstName("Test");
            testEmployee.setLastName("Employee");
            testEmployee.setDepartment("IT");
            testEmployee.setDesignation("Developer");
            testEmployee.setOnboardingStatus("NOT_STARTED");
            testEmployee.setEmail("test@mentis.com");
            testEmployee.setPersonalEmail("test@mentis.com");

            employeeService.saveEmployee(testEmployee);
            return "Test employee created: " + testEmployee.getEmployeeId();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/set-status/{employeeId}/{status}")
    @ResponseBody
    public String setEmployeeStatus(@PathVariable String employeeId, @PathVariable String status) {
        Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);
        if (employeeOpt.isPresent()) {
            Employee employee = employeeOpt.get();
            employee.setOnboardingStatus(status);
            employeeService.saveEmployee(employee);
            return "Status updated to: " + status;
        }
        return "Employee not found";
    }

    /* ---------- DEBUG: TEST UPLOAD ENDPOINT ---------- */
    @GetMapping("/test-upload/{employeeId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testUpload(@PathVariable String employeeId) {
        try {
            Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);

            if (employeeOpt.isPresent()) {
                Employee employee = employeeOpt.get();
                List<OnboardingDocument> documents = documentService.getDocumentsByEmployee(employee);

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("employeeId", employeeId);
                response.put("employeeName", employee.getFirstName() + " " + employee.getLastName());
                response.put("onboardingStatus", employee.getOnboardingStatus());
                response.put("totalDocuments", employee.getTotalDocuments());
                response.put("submittedDocuments", employee.getSubmittedDocuments());
                response.put("verifiedDocuments", employee.getVerifiedDocuments());
                response.put("documentsCount", documents.size());
                response.put("message", "Employee is ready for onboarding");

                logger.info("DEBUG: Employee {} is accessible", employeeId);
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Employee not found");
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /* ---------- TEST NOTIFICATION ---------- */
    @GetMapping("/send-test-notification/{employeeId}")
    @ResponseBody
    public String sendTestNotification(@PathVariable String employeeId) {
        try {
            Employee employee = employeeService.getEmployeeByEmployeeId(employeeId)
                    .orElseThrow(() -> new RuntimeException("Employee not found"));

            notificationService.notifyDeadlineApproaching(employee, 24);

            return "Test notification sent to " + employeeId;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
