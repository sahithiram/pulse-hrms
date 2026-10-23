package com.mentis.hrms.pm.security;

import com.mentis.hrms.pm.realtime.PmEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class PmCurrentUserInterceptor implements HandlerInterceptor {

    private final PmEventPublisher events;

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        try {
            String m = req.getMethod();
            boolean write = "POST".equals(m) || "PUT".equals(m) || "PATCH".equals(m) || "DELETE".equals(m);
            if (write && ex == null && res.getStatus() < 400) {
                PmUserProvider.PmUser u = PmCurrentUserHolder.get();
                events.publish(m, req.getRequestURI(), u != null ? u.employeeId() : null);
            }
        } finally {
            PmCurrentUserHolder.clear();
        }
    }
}