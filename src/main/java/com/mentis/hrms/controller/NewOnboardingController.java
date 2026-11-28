package com.mentis.hrms.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.OnboardingDocument;
import com.mentis.hrms.service.DocumentService;
import com.mentis.hrms.service.EmployeeService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sprint 8 (REQ-8-05) — slim page controller after the split.
 * Document endpoints moved to OnboardingDocumentController,
 * initialize/deadline actions to OnboardingAdminController,
 * /debug/** endpoints to OnboardingDebugController.
 * All original /onboarding URLs are preserved 1:1.
 */
@Controller
@RequestMapping("/onboarding")
public class NewOnboardingController {
    private static final Logger logger = LoggerFactory.getLogger(NewOnboardingController.class);

    @Autowired
    private DocumentService documentService;

    @Autowired
    private EmployeeService employeeService;

    private void addSidebarContext(Model model, HttpSession session) {
        String userRole = session != null ? (String) session.getAttribute("userRole") : null;
        String userName = session != null ? (String) session.getAttribute("userName") : null;
        boolean isSuperAdmin = "SUPER_ADMIN".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole);
        boolean isHR = "HR".equalsIgnoreCase(userRole);
        model.addAttribute("isSuperAdmin", isSuperAdmin);
        model.addAttribute("isHR", isHR);
        model.addAttribute("userName", (userName != null && !userName.isEmpty()) ? userName : "HR Admin");
    }

    // MAIN ONBOARDING DASHBOARD
    @GetMapping
    public String showOnboardingDashboard(Model model, HttpSession session) {
        logger.info("=== LOADING ONBOARDING DASHBOARD ===");
        addSidebarContext(model, session);
        try {
            List<Employee> allEmployees = employeeService.getAllEmployees();

            List<Employee> employeesForOnboarding = allEmployees.stream()
                    .filter(emp -> {
                        String status = emp.getOnboardingStatus();
                        return status != null &&
                                (status.equals("NOT_STARTED") ||
                                        status.equals("DOCUMENTS_PENDING") ||
                                        status.equals("DOCUMENTS_SUBMITTED") ||
                                        status.equals("IN_PROGRESS"));
                    })
                    .collect(Collectors.toList());

            List<Employee> allOnboardingEmployees = allEmployees.stream()
                    .filter(emp -> {
                        String status = emp.getOnboardingStatus();
                        return status != null &&
                                (!status.equals("COMPLETED") &&
                                        !status.equals("CANCELLED"));
                    })
                    .collect(Collectors.toList());

            model.addAttribute("employees", employeesForOnboarding);
            model.addAttribute("allOnboardingEmployees", allOnboardingEmployees);
            model.addAttribute("totalPending", employeesForOnboarding.size());
            model.addAttribute("totalInProgress", allOnboardingEmployees.size());

            logger.info("Dashboard loaded: {} employees for onboarding", employeesForOnboarding.size());
            return "onboarding/dashboard";

        } catch (Exception e) {
            logger.error("Error loading onboarding dashboard: {}", e.getMessage(), e);
            model.addAttribute("error", "Failed to load onboarding dashboard");
            model.addAttribute("employees", new ArrayList<>());
            model.addAttribute("allOnboardingEmployees", new ArrayList<>());
            model.addAttribute("totalPending", 0);
            model.addAttribute("totalInProgress", 0);
            return "onboarding/dashboard";
        }
    }

    // INDIVIDUAL EMPLOYEE ONBOARDING
    @GetMapping("/employee/{employeeId}")
    public String showEmployeeOnboarding(@PathVariable String employeeId,
                                         Model model,
                                         HttpSession session,
                                         RedirectAttributes ra) {
        logger.info("=== LOADING ONBOARDING FOR EMPLOYEE: {} ===", employeeId);
        addSidebarContext(model, session);

        Optional<Employee> employeeOpt = employeeService.getEmployeeByEmployeeId(employeeId);

        if (employeeOpt.isEmpty()) {
            logger.error("Employee not found: {}", employeeId);
            ra.addFlashAttribute("error", "Employee not found: " + employeeId);
            return "redirect:/onboarding";
        }

        Employee employee = employeeOpt.get();
        List<OnboardingDocument> documents = documentService.getDocumentsByEmployee(employee);

        model.addAttribute("employee", employee);
        model.addAttribute("documents", documents);
        model.addAttribute("progress", calculateOnboardingProgress(employee));

        model.addAttribute("totalPending", 0);
        model.addAttribute("offerCount", 0);

        logger.info("Loaded {} documents for employee {}", documents.size(), employeeId);
        return "onboarding/employee-dashboard";
    }

    /* ---------- GET ONBOARDING EMPLOYEE LIST (WITH REAL-TIME UPDATES) ---------- */
    @GetMapping("/onboarding-list")
    @ResponseBody
    public org.springframework.http.ResponseEntity<List<Map<String, Object>>> getOnboardingEmployees() {
        List<Map<String, Object>> response = new ArrayList<>();

        try {
            List<Employee> employees = employeeService.getAllEmployees().stream()
                    .filter(emp -> {
                        String status = emp.getOnboardingStatus();
                        return status != null && !"NOT_STARTED".equals(status);
                    })
                    .collect(Collectors.toList());

            for (Employee emp : employees) {
                Map<String, Object> empData = new HashMap<>();
                empData.put("id", emp.getId());
                empData.put("employeeId", emp.getEmployeeId());
                empData.put("name", emp.getFirstName() + " " + emp.getLastName());
                empData.put("department", emp.getDepartment());
                empData.put("designation", emp.getDesignation());
                empData.put("onboardingStatus", emp.getOnboardingStatus());
                empData.put("totalDocuments", emp.getTotalDocuments());
                empData.put("submittedDocuments", emp.getSubmittedDocuments());
                empData.put("verifiedDocuments", emp.getVerifiedDocuments());
                empData.put("progress", calculateOnboardingProgress(emp));
                empData.put("lastUpdated", java.time.LocalDateTime.now().toString());

                response.add(empData);
            }

            return org.springframework.http.ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Error getting onboarding list: {}", e.getMessage());
            return org.springframework.http.ResponseEntity.badRequest().body(response);
        }
    }

    /* ---------- PROGRESS CALCULATION ---------- */
    private Map<String, Object> calculateOnboardingProgress(Employee employee) {
        Map<String, Object> progress = new HashMap<>();

        List<OnboardingDocument> documents = documentService.getDocumentsByEmployee(employee);

        int total = documents.size();
        int submitted = (int) documents.stream()
                .filter(doc -> "SUBMITTED".equals(doc.getStatus()) ||
                        "VERIFIED".equals(doc.getStatus()) ||
                        "REJECTED".equals(doc.getStatus()))
                .count();
        int verified = (int) documents.stream()
                .filter(doc -> "VERIFIED".equals(doc.getStatus()))
                .count();
        int pending = total - submitted;

        progress.put("totalDocuments", total);
        progress.put("submittedDocuments", submitted);
        progress.put("verifiedDocuments", verified);
        progress.put("pendingDocuments", pending);
        progress.put("submissionProgress", total > 0 ? (submitted * 100) / total : 0);
        progress.put("verificationProgress", total > 0 ? (verified * 100) / total : 0);
        progress.put("pendingVerification", submitted - verified);

        logger.debug("Progress for {}: Total={}, Submitted={}, Verified={}, Pending={}",
                employee.getEmployeeId(), total, submitted, verified, pending);

        return progress;
    }
}
