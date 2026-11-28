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

import java.io.Serializable;
import java.util.Optional;

/**
 * Resolves the logged-in PM user.
 *
 * PERF: every /api/pm/* call used to load the whole Employee row (plus its eager "benefits" collection)
 * just to learn the id/name/role. The result is now kept in the HTTP session for
 * {@code pm.auth.cache-seconds} (default 60). A role change therefore takes at most that long to apply.
 * Set pm.auth.cache-seconds=0 to switch the cache off.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SessionPmUserProvider implements PmUserProvider {

    private static final String CACHE_ATTR = "pm.user.cache";

    private final EmployeeRepository employeeRepository;

    @Value("${pm.auth.dev-fallback:false}")
    private boolean devFallback;

    @Value("${pm.auth.dev-fallback-employee-id:SA001}")
    private String devFallbackEmployeeId;

    @Value("${pm.auth.cache-seconds:60}")
    private long cacheSeconds;

    /** Small serializable snapshot kept in the session. */
    private static final class CachedUser implements Serializable {
        private static final long serialVersionUID = 1L;
        final Long pk;
        final String employeeId;
        final String fullName;
        final String email;
        final String role;
        final long expiresAt;

        CachedUser(PmUser u, long expiresAt) {
            this.pk = u.employeePk();
            this.employeeId = u.employeeId();
            this.fullName = u.fullName();
            this.email = u.email();
            this.role = u.hrmsRole() != null ? u.hrmsRole().name() : null;
            this.expiresAt = expiresAt;
        }

        PmUser toUser() {
            UserRole r = null;
            try { if (role != null) r = UserRole.valueOf(role); } catch (IllegalArgumentException ignored) { }
            return new PmUser(pk, employeeId, fullName, email, r);
        }
    }

    @Override
    public PmUser resolve(HttpServletRequest request) {

        // 1. Session
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object raw = session.getAttribute("userId");
            if (raw != null) {
                String employeeId = String.valueOf(raw);

                // 1a. cached snapshot for this very login
                if (cacheSeconds > 0) {
                    Object c = session.getAttribute(CACHE_ATTR);
                    if (c instanceof CachedUser cu
                            && cu.expiresAt > System.currentTimeMillis()
                            && employeeId.equals(cu.employeeId)) {
                        return cu.toUser();
                    }
                }

                // 1b. database lookup
                Optional<Employee> emp = employeeRepository.findByEmployeeId(employeeId);
                if (emp.isPresent()) {
                    PmUser user = toPmUser(emp.get());
                    if (cacheSeconds > 0) {
                        try {
                            session.setAttribute(CACHE_ATTR,
                                    new CachedUser(user, System.currentTimeMillis() + cacheSeconds * 1000L));
                        } catch (IllegalStateException ignored) { /* session already invalidated */ }
                    }
                    return user;
                }
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
        String last = e.getLastName() == null ? "" : e.getLastName();
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) return full;
        if (e.getEmployeeName() != null && !e.getEmployeeName().isBlank()) return e.getEmployeeName();
        if (e.getEmail() != null) return e.getEmail();
        return "Employee #" + e.getId();
    }
}
