package com.mentis.hrms.filter;

import com.mentis.hrms.service.AuditService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Double-submit-cookie CSRF protection (REQ-2-07).\n * Modes (hrms.security.csrf):\n *   off    → disabled entirely (emergency rollback)\n *   audit  → cookie minted + missing/invalid tokens LOGGED, request still processed (DEFAULT)\n *   enforce→ state-changing requests without a valid token get 403\n * Enablement steps: see 03_CSRF_ROLLOUT.md in the sprint package.\n */
@Component
@Order(2)
public class CsrfAuditFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CsrfAuditFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String COOKIE = "XSRF-TOKEN";
    private static final String HEADER = "X-CSRF-TOKEN";
    private static final String PARAM  = "_csrf";

    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    @Value("${hrms.security.csrf:audit}")
    private String mode;

    public CsrfAuditFilter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String m = mode == null ? "audit" : mode.trim().toLowerCase();
        if ("off".equals(m)) { chain.doFilter(request, response); return; }

        // Ensure a token cookie exists (lazy mint)
        String token = readCookie(request, COOKIE);
        if (token == null) {
            token = newToken();
            Cookie c = new Cookie(COOKIE, token);
            c.setHttpOnly(false);        // must be readable by JS for AJAX POSTs (double-submit)
            c.setSecure(request.isSecure());
            c.setPath("/");
            c.setAttribute("SameSite", "Lax");
            response.addCookie(c);
        }
        // Expose to Thymeleaf via request attribute
        request.setAttribute("csrfToken", token);

        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            chain.doFilter(request, response);
            return;
        }

        String supplied = request.getHeader(HEADER);
        if (supplied == null) supplied = request.getParameter(PARAM);

        boolean valid = token.equals(supplied);
        if (valid) { chain.doFilter(request, response); return; }

        // Only enforce for session-authenticated flows; candidate/public auth surface uses its own guard
        if ("enforce".equals(m)) {
            logger.warn("CSRF_REJECT path={} method={} ip={}", request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
            auditService.audit("CSRF_REJECT", null, null, "http_path", request.getRequestURI(),
                    null, null, request, "{\"method\":\"" + request.getMethod() + "\"}");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"CSRF_TOKEN_INVALID\"}");
            return;
        }

        // audit mode: log and allow
        logger.warn("CSRF_MISSING_OR_INVALID path={} method={} ip={}", request.getRequestURI(), request.getMethod(), request.getRemoteAddr());
        auditService.audit("CSRF_AUDIT", null, null, "http_path", request.getRequestURI(),
                null, null, request, "{\"method\":\"" + request.getMethod() + "\"}");
        chain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) if (name.equals(c.getName())) return c.getValue();
        return null;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
