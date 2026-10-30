package com.mentis.hrms.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;                    // ✅ PHASE 4 (F-P4)
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.mentis.hrms.pm.security.PmPageGuard;
import org.springframework.beans.factory.annotation.Autowired;
@Component
public class RoleInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RoleInterceptor.class);

    /** ✅ PHASE 4 (F-P4): gate for opening PM to HR / HR_MANAGER / EMPLOYEE. Keep false until the audit is fully remediated. */
    @Value("${pm.open-to-all-roles:false}")
    private boolean pmOpenToAllRoles;
    @Autowired
    private PmPageGuard pmGuard;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        logger.debug("Interceptor - URI: {}, Method: {}", uri, method);

        // ============ PUBLIC ENDPOINTS - NO AUTH REQUIRED ============
        if (uri.equals("/candidate/login") ||
                uri.equals("/candidate/auth/login") ||
                uri.startsWith("/candidate/forgot-password") ||
                uri.startsWith("/candidate/reset-password") ||
                uri.startsWith("/candidate/create-password")) {
            logger.debug("Public auth endpoint - allowing access");
            return true;
        }

        // ✅ PHASE 4 (F-P23): removed the unauthenticated "/data/" bypass.
        // Static data lives under /static/ and is already allowed by the static-resource block below.
        // Do not re-add a bare /data/ bypass here.

        // Allow resume endpoints
        // ✅ PHASE 4 (F-P5): a substring match on "/public/" would have bypassed every /api/pm/** endpoint.
        // Restrict this fast path so it can never touch the PM module.
        if (!uri.startsWith("/api/pm/") && !uri.startsWith("/pm/") &&
                (uri.contains("/download-resume") ||
                        uri.contains("/preview-resume") ||
                        uri.contains("/preview-image") ||
                        uri.contains("/public/"))) {
            logger.debug("Resume endpoint - allowing access without authentication: {}", uri);
            return true;
        }

        // Allow static resources
        if (uri.startsWith("/static/") ||
                uri.startsWith("/webjars/") ||
                uri.startsWith("/uploads/") ||
                uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.startsWith("/images/")) {
            logger.debug("Static resource - allowing access");
            return true;
        }

        // ============ SESSION CHECK ============
        if (session == null || session.getAttribute("userId") == null) {
            logger.warn("No active session or userId not found - redirecting to login. URI: {}", uri);

            if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With")) || uri.startsWith("/api/")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Session expired\",\"redirect\":\"/candidate/login\"}");
                return false;
            }

            response.sendRedirect("/candidate/login?error=Please+login+first");
            return false;
        }

        // Get user details from session
        String role = (String) session.getAttribute("userRole");
        String empId = (String) session.getAttribute("userId");
        String loginType = (String) session.getAttribute("loginType");

        logger.debug("User authenticated - Role: {}, UserId: {}, LoginType: {}", role, empId, loginType);

        // ============ SUPER_ADMIN HAS ACCESS TO EVERYTHING (including /pm) ============
        if ("SUPER_ADMIN".equals(role)) {
            logger.debug("SUPER_ADMIN access granted for URI: {}", uri);
            return true;
        }

        // ============ PM MODULE ============
        if (uri.equals("/pm") || uri.startsWith("/pm/") || uri.startsWith("/api/pm/")) {
            if (uri.startsWith("/api/pm/dev")) return deny(role, empId, uri, response); // dev triggers: SA only

            // ✅ PHASE 4 (F-P4): was always true for HR / HR_MANAGER / EMPLOYEE.
            // Now gated behind pm.open-to-all-roles (default false) until every /api/pm method
            // has an object-level check (Rule 7).
            boolean pmModuleRole = "PM".equals(role)
                    || (pmOpenToAllRoles && ("HR".equals(role)
                    || "HR_MANAGER".equals(role) || "EMPLOYEE".equals(role)));
            if (pmModuleRole) return true;   // PmAccessService decides what each person may do

// Team leads / project leads who are plain EMPLOYEEs (cached 60s per session)
            if ("EMPLOYEE".equals(role) && employeeMayUsePm(session, empId)) return true;

            return deny(role, empId, uri, response);
        }

        // ============ ADMIN DASHBOARD ACCESS ============
        if (uri.startsWith("/dashboard/admin")) {
            if (!"SUPER_ADMIN".equals(role)) {
                logger.warn("Non-Admin user attempting to access Admin dashboard. Role: {}, URI: {}", role, uri);
                return redirectToHomeBasedOnRole(role, empId, response);
            }
            logger.debug("Admin access granted for URI: {}", uri);
            return true;
        }

        boolean isHrRole = "HR".equals(role) || "HR_MANAGER".equals(role);

        // whole recruitment and HR area, legacy /dashboard/* included
        if (uri.startsWith("/dashboard") && !isHrRole) return deny(role, empId, uri, response);

        // AI chat endpoints are role-specific
        if (uri.startsWith("/api/chat/admin")) return deny(role, empId, uri, response);          // SA already returned
        if (uri.startsWith("/api/chat/hr") && !isHrRole) return deny(role, empId, uri, response);

        // ============ EMPLOYEE/CANDIDATE DASHBOARD ACCESS ============
        if (uri.startsWith("/candidate/dashboard")) {
            String[] pathParts = uri.split("/");
            String requestedEmpId = null;

            for (int i = 0; i < pathParts.length - 1; i++) {
                if ("dashboard".equals(pathParts[i]) && i + 1 < pathParts.length) {
                    requestedEmpId = pathParts[i + 1];
                    break;
                }
            }

            if (requestedEmpId != null && !requestedEmpId.equals(empId)) {
                logger.warn("Employee {} attempting to access dashboard of employee {}", empId, requestedEmpId);
                response.sendRedirect("/candidate/dashboard/" + empId);
                return false;
            }

            if (!("EMPLOYEE".equals(role) || "PM".equals(role))) {
                logger.warn("Non-employee attempting to access employee dashboard. Role: {}, URI: {}", role, uri);
                return redirectToHomeBasedOnRole(role, empId, response);
            }

            logger.debug("Employee dashboard access granted for URI: {}", uri);
            return true;
        }

        // ============ PREVENT EMPLOYEE ACCESS TO HR DASHBOARD ============
        if (uri.startsWith("/dashboard") && !uri.startsWith("/dashboard/admin") && !uri.startsWith("/dashboard/hr")) {
            if ("EMPLOYEE".equals(role) || "EMPLOYEE".equals(loginType)) {
                logger.warn("Employee attempting to access HR dashboard - redirecting. URI: {}", uri);
                response.sendRedirect("/candidate/dashboard/" + empId);
                return false;
            }
        }

        // Allow admin attendance APIs (they have their own security checks)
        if (uri.startsWith("/dashboard/admin/attendance/api/")) {
            logger.debug("Admin attendance API endpoint - allowing access");
            return true;
        }

        // Default: allow access
        logger.debug("Default access granted for URI: {}", uri);
        return true;
    }
    private boolean employeeMayUsePm(HttpSession session, String empId) {
        long now = System.currentTimeMillis();
        Long at = (Long) session.getAttribute("pmAccessCheckedAt");
        Boolean ok = (Boolean) session.getAttribute("pmAccessOk");
        if (at != null && ok != null && now - at < 60_000) return ok;
        boolean result = pmGuard.canOpenPmByCode(empId);
        session.setAttribute("pmAccessOk", result);
        session.setAttribute("pmAccessCheckedAt", now);
        return result;
    }
    private boolean redirectToHomeBasedOnRole(String role, String empId, HttpServletResponse response) throws Exception {
        if ("SUPER_ADMIN".equals(role)) {
            logger.debug("Redirecting to Super Admin dashboard");
            response.sendRedirect("/dashboard/admin");
        } else if ("HR".equals(role) || "HR_MANAGER".equals(role)) {
            logger.debug("Redirecting to HR dashboard");
            response.sendRedirect("/dashboard/hr");
        } else if ("PM".equals(role)) {
            logger.debug("Redirecting to PM home");
            response.sendRedirect("/pm/01-home");
        } else if ("EMPLOYEE".equals(role)) {
            logger.debug("Redirecting to Employee dashboard: {}", empId);
            response.sendRedirect("/candidate/dashboard/" + empId);
        } else {
            logger.debug("Redirecting to login page");
            response.sendRedirect("/candidate/login");
        }
        return false;
    }

    private boolean deny(String role, String empId, String uri, HttpServletResponse response) throws Exception {
        logger.warn("Role {} denied for {}", role, uri);
        if (uri.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden\"}");
            return false;
        }
        return redirectToHomeBasedOnRole(role, empId, response);
    }
}