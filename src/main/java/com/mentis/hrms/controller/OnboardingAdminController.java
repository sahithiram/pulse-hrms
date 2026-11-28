package com.mentis.hrms.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.service.DocumentService;
import com.mentis.hrms.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Sprint 8 (REQ-8-05) — administrative lifecycle actions split out of NewOnboardingController:
 * initialize onboarding checklists and manage document deadlines. URLs preserved 1:1.
 */
@Controller
@RequestMapping("/onboarding")
public class OnboardingAdminController {
    private static final Logger logger = LoggerFactory.getLogger(OnboardingAdminController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EmployeeService employeeService;

    /* ---------- INITIALIZE ONBOARDING (Form-based) ---------- */
    @PostMapping("/employee/{employeeId}/initialize")
    public String initializeOnboarding(@PathVariable String employeeId, RedirectAttributes ra) {
        try {
            Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (employeeOpt.isPresent()) {
                documentService.initializeDocumentChecklist(employeeOpt.get());
                ra.addFlashAttribute("success", "Onboarding initialized successfully!");
            } else {
                ra.addFlashAttribute("error", "Employee not found");
            }
        } catch (Exception e) {
            logger.error("Error initializing onboarding: {}", e.getMessage());
            ra.addFlashAttribute("error", "Failed to initialize onboarding: " + e.getMessage());
        }
        return "redirect:/onboarding/employee/" + employeeId;
    }

    /* ---------- INITIALIZE ONBOARDING (JSON API) ---------- */
    @PostMapping("/employee/{employeeId}/initialize-api")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> initializeOnboardingApi(@PathVariable String employeeId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);

            if (employeeOpt.isEmpty()) {
                response.put("success", false);
                response.put("error", "Employee not found");
                return ResponseEntity.badRequest().body(response);
            }

            Employee employee = employeeOpt.get();

            documentService.initializeDocumentChecklist(employee);
            employee.setOnboardingStatus("DOCUMENTS_PENDING");
            employeeService.saveEmployee(employee);

            response.put("success", true);
            response.put("message", "Onboarding initialized successfully");
            response.put("employeeId", employeeId);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error initializing onboarding for {}: {}", employeeId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- SET DEADLINE ---------- */
    @PostMapping("/employee/{employeeId}/set-deadline")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setDeadline(
            @PathVariable String employeeId,
            @RequestParam String deadline) {

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<Employee> empOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (empOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Employee not found: " + employeeId));
            }

            Employee employee = empOpt.get();
            LocalDateTime deadlineDateTime = LocalDateTime.parse(deadline);

            employee.setDocumentDeadline(deadlineDateTime);
            employee.setDeadlineWarningSent(false);
            employee.setDeadlineFinalSent(false);
            employeeService.saveEmployee(employee);

            response.put("success", true);
            response.put("message", "Deadline set successfully");
            response.put("deadline", deadlineDateTime.toString());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to set deadline: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- TEST DEADLINE ---------- */
    @GetMapping("/employee/{employeeId}/set-deadline-test")
    @ResponseBody
    public String setTestDeadline(@PathVariable String employeeId) {
        try {
            Optional<Employee> empOpt = employeeService.getEmployeeByEmployeeId(employeeId);
            if (empOpt.isEmpty()) return "Employee not found";

            Employee employee = empOpt.get();
            employee.setDocumentDeadline(LocalDateTime.now().plusDays(2));
            employee.setDeadlineWarningSent(false);
            employee.setDeadlineFinalSent(false);
            employeeService.saveEmployee(employee);

            return "Deadline set to 2 days from now for " + employeeId;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
