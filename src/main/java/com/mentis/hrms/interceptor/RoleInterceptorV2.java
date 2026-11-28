package com.mentis.hrms.interceptor;

import com.mentis.hrms.service.AuditService;
import com.mentis.hrms.util.Roles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Deny-by-default access control (REQ-2-02/03).\n * PUBLIC: exact whitelist only — no substring tricks, no prefix leaks.\n * AUTHED: session with userId (String employee business key, e.g. "EMP001"); SUPER_ADMIN bypass; PM gates preserved.\n * EVERYTHING ELSE: 403 (page) or 401 JSON (api).\n *\n * Rollout: hrms.security.mode = enforce | audit | off   (default: enforce)\n *   audit → would-deny decisions are LOGGED as WOULD_DENY and allowed (staging week 1)\n *   off   → pre-sprint behaviour (emergency rollback only)\n */
@Component
public class RoleInterceptorV2 implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RoleInterceptorV2.class);

    /** Exact public paths (no wildcards, no substring matching). */
    private static final Set<String> PUBLIC_EXACT = Set.of(
            "/", "/home", "/job-description", "/login", "/actuator/health", "/favicon.ico", "/error",
            "/candidate/login", "/candidate/create-password", "/candidate/logout");

    /** Narrow public prefixes; each may accept the methods its handler needs. */
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/css/", "/js/", "/images/", "/webjars/", "/static/", "/data/",
            // Candidate + employee authentication surface (verified against CandidateAuthController + CandidateOnboardingController):
            "/candidate/auth/",             // POST /candidate/auth/login, GET+POST forgot-password & reset-password
            "/candidate/reset-password/");  // reset link landing pages

    private static final List<String> API_PREFIXES = List.of("/api/");

    @Value("${hrms.security.mode:enforce}")
    private String mode;

    @Value("${pm.open-to-all-roles:false}")
    private boolean pmOpenToAll;

    private final AuditService auditService;

    public RoleInterceptorV2(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = normalize(request.getRequestURI());
        String method = request.getMethod();

        // 1) Public exact/prefix — GET/HEAD only except the auth surface which also takes POST
        if (isPublic(path, method)) return true;

        // 2) Session required
        HttpSession session = request.getSession(false);
        String role = session != null ? (String) session.getAttribute("userRole") : null;
        String actorRef = null;
        Long actorId = null;
        if (session != null) {
            Object u = session.getAttribute("userId");
            if (u != null) {
                actorRef = String.valueOf(u);              // "EMP001" in this app
                try { actorId = Long.valueOf(actorRef); } catch (NumberFormatException ignored) { }
            }
        }

        boolean authed = session != null && actorRef != null;
        if (!authed) {
            return denyOrAudit(request, response, path, actorId, actorRef, role,
                    "UNAUTHENTICATED", isApi(path) ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_SEE_OTHER);
        }

        // 3) SUPER_ADMIN bypass (documented, by design)
        if (Roles.isSuperAdmin(role)) return true;

        // 4) PM gates preserved from the legacy interceptor (R12)
        if (path.startsWith("/pm") || path.startsWith("/api/pm")) {
            if (pmOpenToAll || Roles.is(role, "PM") || Roles.isHr(role)) return true;
            return denyOrAudit(request, response, path, actorId, actorRef, role, "PM_FORBIDDEN",
                    HttpServletResponse.SC_FORBIDDEN);
        }

        // 5) Permission admin endpoint — SUPER_ADMIN only
        if (path.startsWith("/api/permissions")) {
            return denyOrAudit(request, response, path, actorId, actorRef, role, "PERMISSIONS_FORBIDDEN",
                    HttpServletResponse.SC_FORBIDDEN);
        }

        // 6) Audit viewer — SUPER_ADMIN / HR / HR_MANAGER
        if (path.startsWith("/api/audit-log")) {
            if (Roles.isHr(role)) return true;
            return denyOrAudit(request, response, path, actorId, actorRef, role, "AUDIT_FORBIDDEN",
                    HttpServletResponse.SC_FORBIDDEN);
        }

        // 7) Everything else authenticated-and-known-role passes the coarse gate.
        //    FINE-GRAINED object scope lives in AccessPolicy (service layer).
        if (role == null || role.isBlank()) {
            return denyOrAudit(request, response, path, actorId, actorRef, role, "UNKNOWN_ROLE",
                    HttpServletResponse.SC_FORBIDDEN);
        }
        return true;
    }

    private boolean isPublic(String path, String method) {
        String m = method == null ? "GET" : method.toUpperCase();
        boolean safeMethod = "GET".equals(m) || "HEAD".equals(m);
        if (PUBLIC_EXACT.contains(path)) return safeMethod || path.equals("/error");
        for (String prefix : PUBLIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                if (prefix.startsWith("/candidate/auth/") || prefix.startsWith("/candidate/reset-password/")) {
                    return true; // login + forgot/reset accept POST
                }
                return safeMethod;
            }
        }
        // Candidate auth view pages that need POST too:
        if (path.equals("/candidate/create-password")) return true;
        return false;
    }

    private boolean isApi(String path) {
        for (String p : API_PREFIXES) if (path.startsWith(p)) return true;
        return false;
    }

    private boolean denyOrAudit(HttpServletRequest request, HttpServletResponse response,
                                String path, Long actorId, String actorRef, String role,
                                String reason, int status) throws IOException {
        String enforce = mode == null ? "enforce" : mode.trim().toLowerCase();
        String details = "{\"method\":\"" + request.getMethod() + "\",\"actorRef\":\"" + safe(actorRef) + "\"}";
        if ("off".equals(enforce)) {
            return true;
        }
        if ("audit".equals(enforce)) {
            logger.warn("WOULD_DENY [{}] path={} method={} actor={} role={}", reason, path, request.getMethod(), actorRef, role);
            auditService.audit("WOULD_DENY_" + reason, actorId, role, "http_path", path,
                    null, null, request, details);
            return true;
        }
        boolean api = isApi(path);
        logger.warn("DENY [{}] path={} method={} actor={} role={}", reason, path, request.getMethod(), actorRef, role);
        auditService.audit("DENIED_" + reason, actorId, role, "http_path", path,
                null, null, request, details);
        if (api) {
            response.setStatus(status);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + reason + "\"}");
        } else {
            if (status == HttpServletResponse.SC_SEE_OTHER) {
                response.sendRedirect("/candidate/login?error=auth");
            } else {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("text/plain");
                response.getWriter().write("403 Forbidden");
            }
        }
        return false;
    }

    private String normalize(String path) {
        if (path == null) return "/";
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path.replaceAll("/{2,}", "/");
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("\"", "'");
    }
}
