package com.mentis.hrms.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.JobApplication;
import com.mentis.hrms.model.OfferLetter;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dashboard/admin")
public class AdminDashboardController {

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private JobApplicationService applicationService;

    @Autowired
    private OfferService offerService;

    @GetMapping
    public String adminDashboard(Model model, HttpSession session, RedirectAttributes ra) {
        // Verify authentication
        if (session.getAttribute("userId") == null) {
            ra.addFlashAttribute("error", "Session expired. Please login again.");
            return "redirect:/candidate/auth/login";
        }

        // Verify Super Admin role
        String role = (String) session.getAttribute("userRole");
        System.out.println("AdminDashboardController - User role: " + role);

        if (!"SUPER_ADMIN".equals(role)) {
            System.out.println("Unauthorized access attempt to admin dashboard by role: " + role);
            return "redirect:/candidate/login?error=Unauthorized";
        }

        // FIX: the shared "dashboard" template (also used by HrDashboardController) reads
        // jobs / departments / recentApplications / interviewsScheduled / inOnboarding /
        // offerCount via th:each and th:text. AdminDashboardController never populated these,
        // so Thymeleaf hit a null collection mid-render and the response (already streaming
        // as chunked HTTP) died with a "chunked encoding" error in the browser console instead
        // of showing a clean error page. Loading the same base dashboard data HrDashboardController
        // already uses fixes that, without touching any other feature.
        dashboardService.loadDashboardData(model);

        // Load comprehensive system statistics (super-admin-only widgets)
        loadSuperAdminStats(model);

        // Add user info for header
        // Add user info for header
        model.addAttribute("isSuperAdmin", true);
        model.addAttribute("isHR", false); // FIX: dashboard.html line 4414 evaluates
        // "isHR and !isSuperAdmin" — if isHR is
        // never set, SpEL throws
        // "cannot convert from null to boolean"
        // mid-render, which aborts the chunked
        // response and causes
        // ERR_INCOMPLETE_CHUNKED_ENCODING in the browser.
        model.addAttribute("userRole", role);
        model.addAttribute("userName", session.getAttribute("userName"));
        // Return the dashboard view (not redirect)
        return "dashboard"; // This should be your dashboard.html template
    }

    private void loadSuperAdminStats(Model model) {
        // Fetch all system data
        List<Employee> allUsers = employeeService.getAllEmployees();
        List<JobApplication> allApplications = applicationService.getAllApplications();
        List<OfferLetter> allOffers = offerService.getAllOfferLetters();

        // Calculate role distribution
        long superAdminCount = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.SUPER_ADMIN)
                .count();
        long hrCount = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.HR)
                .count();
        long employeeCount = allUsers.stream()
                .filter(u -> u.getRole() == UserRole.EMPLOYEE)
                .count();

        // Calculate active/inactive
        long activeUsers = allUsers.stream().filter(Employee::isActive).count();
        long inactiveUsers = allUsers.size() - activeUsers;

        // Calculate monthly hires (defensive: skip applications with a null date instead of
        // throwing NPE on .isAfter())
        java.time.LocalDateTime monthStart = java.time.LocalDateTime.now().withDayOfMonth(1);
        long hiredThisMonth = allApplications.stream()
                .filter(app -> "Hired".equals(app.getStatus()))
                .filter(app -> app.getApplicationDate() != null && app.getApplicationDate().isAfter(monthStart))
                .count();

        // Add to model
        model.addAttribute("totalUsers", allUsers.size());
        model.addAttribute("superAdminCount", superAdminCount);
        model.addAttribute("hrCount", hrCount);
        model.addAttribute("employeeCount", employeeCount);
        model.addAttribute("activeUsers", activeUsers);
        model.addAttribute("inactiveUsers", inactiveUsers);
        model.addAttribute("totalCandidates", allApplications.size());
        model.addAttribute("totalOffers", allOffers.size());
        model.addAttribute("hiredThisMonth", hiredThisMonth);
    }
}
