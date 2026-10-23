package com.mentis.hrms.pm.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/pm")
public class PmViewController {

    @GetMapping({"", "/", "/01-home"})
    public String home() { return "pm/01-home"; }

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

    @GetMapping("/08-clients")
    public String clients() { return "pm/08-clients"; }

    @GetMapping("/09-quality")
    public String quality() { return "pm/09-quality"; }

    @GetMapping("/10-insights")
    public String insights() { return "pm/10-insights"; }

    @GetMapping("/11-settings")
    public String settings() { return "pm/11-settings"; }

    @GetMapping("/06b-graph")
    public String dependencyGraph() { return "pm/06b-graph"; }

}