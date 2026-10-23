package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.PmMeResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.dto.response.TaskSummaryResponse;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.repository.TeamRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.security.PmCurrentUserHolder;
import com.mentis.hrms.pm.security.PmUserProvider;
import com.mentis.hrms.pm.service.MyWorkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("pmMeController")
@RequestMapping("/api/pm/me")
@RequiredArgsConstructor
public class PmMeController {

    private final ProjectRepository projectRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final PmAccessService access;
    private final MyWorkService myWorkService;

    @GetMapping
    public ApiResponse<PmMeResponse> me(@CurrentUserId Long userId) {
        PmUserProvider.PmUser u = PmCurrentUserHolder.get();

        List<Long> ownedProjects = projectRepository.findByOwnerId(userId)
                .stream().map(p -> p.getId()).toList();
        List<Long> leadTeams = teamRepository.findByLeadEmployeeId(userId)
                .stream().map(t -> t.getId()).toList();
        List<Long> memberTeams = teamMemberRepository.findByEmployeeId(userId)
                .stream().map(m -> m.getTeamId()).distinct().toList();

        PmMeResponse res = PmMeResponse.builder()
                .id(u.employeePk())
                .employeeId(u.employeeId())
                .name(u.fullName())
                .email(u.email())
                .avatarInitials(initials(u.fullName()))
                .hrmsRole(u.hrmsRole() != null ? u.hrmsRole().name() : "EMPLOYEE")
                .ownerOfProjectIds(ownedProjects)
                .leadOfTeamIds(leadTeams)
                .memberOfTeamIds(memberTeams)
                .admin(access.isAdmin(userId))
                .canCreateProject(access.canCreateProject(userId))
                .build();
        return ApiResponse.ok(res);
    }
    /** Tasks where the SESSION user is assignee or watcher. userId comes from @CurrentUserId, never the query string. */
    @GetMapping("/tasks")
    public ApiResponse<List<TaskSummaryResponse>> myTasks(@CurrentUserId Long userId) {
        return ApiResponse.ok(myWorkService.listMyTasks(userId));
    }

    /** Projects where the SESSION user is owner, team member, or has at least one task. */
    @GetMapping("/projects")
    public ApiResponse<List<ProjectSummaryResponse>> myProjects(@CurrentUserId Long userId) {
        return ApiResponse.ok(myWorkService.listMyProjects(userId));
    }

    /** KPI numbers for the dashboard widget. */
    @GetMapping("/kpi")
    public ApiResponse<MyWorkService.MyWorkKpi> myKpi(@CurrentUserId Long userId) {
        return ApiResponse.ok(myWorkService.getKpi(userId));
    }


    private String initials(String name) {

        if (name == null || name.isBlank()) return "U";
        String[] parts = name.trim().split("\\s+");
        String a = parts.length > 0 && !parts[0].isEmpty() ? parts[0].substring(0, 1) : "";
        String b = parts.length > 1 && !parts[1].isEmpty() ? parts[1].substring(0, 1) : "";
        String out = (a + b).toUpperCase();
        return out.isEmpty() ? "U" : out;

    }
}