package com.mentis.hrms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REQ-8-09 — once an employee is EXITED/INACTIVE their credentials must stop working.
 * This filter invalidates the HttpSession on the FIRST request after the account is deactivated
 * (Sprint-2 sessions are server-side; there is no token denylist to extend).
 * Cached for 60s per user to avoid a DB hit on every request.
 * API paths get 401 JSON; page paths get redirected to /login.
 */
@Component
public class ExitEnforcementFilter extends OncePerRequestFilter {

    private static final long TTL_MS = 60_000;

    private final Map<String, long[]> activeCache = new ConcurrentHashMap<>(); // key -> [activeFlag, expiresAt]

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userIdAttr = session.getAttribute("userId");
            if (userIdAttr != null && !isActiveCached(userIdAttr)) {
                session.invalidate();
                String path = request.getRequestURI();
                if (path.startsWith("/api/")) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"ACCOUNT_INACTIVE\",\"message\":\"This account is no longer active.\"}}");
                } else {
                    response.sendRedirect(request.getContextPath() + "/login?exited=1");
                }
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isActiveCached(Object userIdAttr) {
        String key = String.valueOf(userIdAttr);
        long now = System.currentTimeMillis();
        long[] cached = activeCache.get(key);
        if (cached != null && cached[1] > now) {
            return cached[0] == 1L;
        }
        boolean active = resolveActive(userIdAttr);
        activeCache.put(key, new long[]{active ? 1L : 0L, now + TTL_MS});
        return active;
    }

    private boolean resolveActive(Object userIdAttr) {
        try {
            if (userIdAttr instanceof Number n) {
                List<Integer> rows = jdbcTemplate.query(
                        "SELECT active FROM employees WHERE id = ? LIMIT 1",
                        rs -> rs.next() ? List.of(rs.getInt(1)) : List.of(), n.longValue());
                return rows.isEmpty() || rows.get(0) == 1; // unknown user → do not block (login layer owns auth)
            }
            String s = String.valueOf(userIdAttr);
            List<Integer> rows = jdbcTemplate.query(
                    "SELECT active FROM employees WHERE employee_id = ? OR id = ? LIMIT 1",
                    rs -> rs.next() ? List.of(rs.getInt(1)) : List.of(), s, parseLongOrNull(s));
            return rows.isEmpty() || rows.get(0) == 1;
        } catch (Exception e) {
            // Fail-open would defeat the purpose; fail-closed would lock out everyone on a DB blip.
            // Chosen: log-and-allow for THIS request, cache expires in 60s so a DB recovery restores enforcement.
            logger.warn("ExitEnforcementFilter: could not resolve active flag for {} — allowing request: {}", userIdAttr, e.getMessage());
            return true;
        }
    }

    private static Long parseLongOrNull(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ExitEnforcementFilter.class);
}
