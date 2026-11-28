package com.mentis.hrms.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Baseline security response headers (REQ-2-07).\n * CSP is OFF by default because inline scripts/styles are used heavily today;\n * set hrms.security.csp to a policy string when the frontend is refactored — see docs.\n */
@Component
@Order(1)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Value("${hrms.security.csp:}")
    private String csp;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "SAMEORIGIN");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("X-XSS-Protection", "0");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (csp != null && !csp.isBlank()) {
            response.setHeader("Content-Security-Policy", csp);
        }
        // Never cache authenticated pages
        String uri = request.getRequestURI();
        if (uri.startsWith("/dashboard") || uri.startsWith("/api")) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
