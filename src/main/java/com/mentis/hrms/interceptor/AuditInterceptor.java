package com.mentis.hrms.interceptor;

import com.mentis.hrms.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.*;

/**
 * Writes an audit row for EVERY state-changing HTTP request.\n * Sanitizes parameters via an allowlist of SAFE keys (never logs passwords/tokens).\n */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SAFE_PARAM_KEYS = Set.of(
            "id", "employeeId", "leaveId", "date", "department", "status", "action", "type", "page", "size");
    private static final Set<String> SENSITIVE_PARAM_KEYS = Set.of(
            "password", "passwd", "token", "resetToken", "authorization", "secret", "apiKey");

    private final AuditService auditService;

    public AuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String method = request.getMethod();
        if (method == null || !MUTATING.contains(method.toUpperCase())) return;

        HttpSession session = request.getSession(false);
        Long actorId = null;
        String role = null;
        String actorRef = null;
        if (session != null) {
            Object u = session.getAttribute("userId");
            if (u != null) {
                actorRef = String.valueOf(u);                    // "EMP001" business key in this app
                try { actorId = Long.valueOf(actorRef); } catch (NumberFormatException ignored) { }
            }
            role = (String) session.getAttribute("userRole");
        }

        String path = request.getRequestURI();

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String[]> e : request.getParameterMap().entrySet()) {
            String key = e.getKey();
            String lower = key.toLowerCase(Locale.ROOT);
            String v = e.getValue() != null && e.getValue().length > 0 ? e.getValue()[0] : "";
            if (v.length() > 100) v = v.substring(0, 100);
            v = v.replace("\"", "'");
            if (SENSITIVE_PARAM_KEYS.contains(lower)) {
                parts.add("\"" + key + "\":\"[REDACTED]\"");
            } else if (SAFE_PARAM_KEYS.contains(lower)) {
                parts.add("\"" + key + "\":\"" + v + "\"");
            }
        }
        if (actorRef != null) parts.add("\"actorRef\":\"" + actorRef.replace("\"", "'") + "\"");
        parts.add("\"status\":" + response.getStatus());
        if (ex != null) {
            parts.add("\"exception\":\"" + ex.getClass().getSimpleName() + "\"");
        }
        String details = "{" + String.join(",", parts) + "}";

        auditService.auditHttpMutation(method.toUpperCase(), path, actorId, role, request, details);
    }
}
