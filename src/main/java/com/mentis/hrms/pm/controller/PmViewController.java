package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmPageGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/pm")
@RequiredArgsConstructor
public class PmViewController {

    private final PmPageGuard guard;

    private String denied(Long userId) {
        return guard.isTeamLeadOnly(userId) ? "redirect:/pm/12-team-lead" : "redirect:/pm/01-home";
    }

    @GetMapping({"", "/", "/01-home"})
    public String home(@CurrentUserId Long userId) {
        return guard.isTeamLeadOnly(userId) ? "redirect:/pm/12-team-lead" : "pm/01-home";
    }

    @GetMapping("/02-inbox")
    public String inbox() { return "pm/02-inbox"; }

    @GetMapping("/03-my-work")
    public String myWork() { return "pm/03-my-work"; }

    @GetMapping("/04-projects")
    public String projects() { return "pm/04-projects"; }

    @GetMapping("/05-planning")
    public String planning() { return "pm/05-planning"; }

    @GetMapping("/06-tasks")
    public String tasks() { return "pm/06-tasks"; }

    @GetMapping("/07-team")
    public String team() { return "pm/07-team"; }

    /** STEP F7: Client Directory is for Super Admin, HR, HR Manager and PM. */
    @GetMapping("/08-clients")
    public String clients(@CurrentUserId Long userId) {
        return guard.canOpenClients(userId) ? "pm/08-clients" : denied(userId);
    }

    @GetMapping("/09-quality")
    public String quality() { return "pm/09-quality"; }

    /** STEP F7: Insights is for admins and the lead tier. */
    @GetMapping("/10-insights")
    public String insights(@CurrentUserId Long userId) {
        return guard.canOpenInsights(userId) && !guard.isTeamLeadOnly(userId) ? "pm/10-insights" : denied(userId);
    }

    /** Settings is every user's own page (profile, theme, access reference), so it stays open. */
    @GetMapping("/11-settings")
    public String settings() { return "pm/11-settings"; }

    /** Team Lead dashboard: only people who lead at least one team (Team.leadEmployeeId). */
    @GetMapping("/12-team-lead")
    public String teamLead(@CurrentUserId Long userId) {
        return guard.isTeamLead(userId) ? "pm/12-team-lead" : "redirect:/pm/01-home";
    }

    @GetMapping("/06b-graph")
    public String dependencyGraph() { return "pm/06b-graph"; }
}