package com.mentis.hrms.pm.config;

import com.mentis.hrms.pm.security.PmPageGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * Adds the model attributes candidate/dashboard.html needs to decide whether to show the
 * "Project Management" tab. Runs only on rendered /candidate/dashboard pages, so it costs
 * nothing on REST calls.
 *   isTeamLead : show the tab (team lead, project LEAD, or owner/MANAGER of a project)
 *   pmHome     : where the tab goes (/pm/12-team-lead for team leads, else /pm/01-home)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PmTabInterceptor implements HandlerInterceptor {

    private final PmPageGuard guard;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView mav) {
        if (mav == null || mav.getViewName() == null || mav.getViewName().startsWith("redirect:")) return;
        HttpSession session = request.getSession(false);
        if (session == null) return;
        Object uid = session.getAttribute("userId");
        if (uid == null) return;
        String code = String.valueOf(uid);
        boolean tab = false, tlOnly = false;
        try {
            tab = guard.canOpenPmByCode(code);
            tlOnly = tab && guard.isTeamLeadOnlyByCode(code);
        } catch (Exception e) {
            log.warn("PM tab check failed for {}: {}", code, e.getMessage());
        }
        mav.getModel().put("isTeamLead", tab);
        mav.getModel().put("pmTabLabel", tlOnly ? "My Team" : "Project Management");
        mav.getModel().put("pmHome", tlOnly ? "/pm/12-team-lead" : "/pm/01-home");
    }
}