package com.mentis.hrms.pm.security;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.enums.UserRole;
import com.mentis.hrms.repository.EmployeeRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionPmUserProvider implements PmUserProvider {

    private final EmployeeRepository employeeRepository;

    @Value("${pm.auth.dev-fallback:false}")
    private boolean devFallback;

    @Value("${pm.auth.dev-fallback-employee-id:SA001}")
    private String devFallbackEmployeeId;

    @Override
    public PmUser resolve(HttpServletRequest request) {

        // 1. Session
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object raw = session.getAttribute("userId");
            if (raw != null) {
                String employeeId = String.valueOf(raw);
                Optional<Employee> emp = employeeRepository.findByEmployeeId(employeeId);
                if (emp.isPresent()) return toPmUser(emp.get());
                log.warn("PM auth: session userId='{}' not found in employees", employeeId);
            }
        }

        // 2. Dev fallback
        if (devFallback) {
            String override = request.getHeader("X-User-Id");
            if (override == null) override = request.getParameter("userId");
            if (override == null) override = devFallbackEmployeeId;

            Optional<Employee> emp = employeeRepository.findByEmployeeId(override);
            if (emp.isPresent()) return toPmUser(emp.get());

            try {
                Long pk = Long.parseLong(override);
                Optional<Employee> byPk = employeeRepository.findById(pk);
                if (byPk.isPresent()) return toPmUser(byPk.get());
            } catch (NumberFormatException ignored) { }
            log.warn("PM auth: dev fallback user '{}' not found", override);
        }

        return null;
    }

    private PmUser toPmUser(Employee e) {
        UserRole role = e.getRole() != null ? e.getRole() : UserRole.EMPLOYEE;
        return new PmUser(
                e.getId(),
                e.getEmployeeId(),
                buildFullName(e),
                e.getEmail(),
                role
        );
    }

    private String buildFullName(Employee e) {
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last  = e.getLastName()  == null ? "" : e.getLastName();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank()) return e.getEmployeeName();
        if (e.getEmail() != null) return e.getEmail();
        return "Employee #" + e.getId();
    }
}