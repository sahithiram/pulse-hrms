package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateTeamRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.TeamResponse;
import com.mentis.hrms.pm.dto.response.TeamSummaryResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mentis.hrms.pm.security.PmAccessService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.mentis.hrms.pm.dto.request.AddTeamMemberRequest;
import com.mentis.hrms.pm.dto.request.UpdateTeamMemberRequest;
import com.mentis.hrms.pm.dto.response.TeamMemberResponse;
@RestController("pmTeamController")
@RequestMapping("/api/pm/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final PmAccessService access;

    @PostMapping
    public ResponseEntity<ApiResponse<TeamResponse>> create(
            @Valid @RequestBody CreateTeamRequest req,
            @CurrentUserId Long userId) {
        access.requireCanCreateTeam(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Team created", teamService.createTeam(req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TeamResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.getTeam(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TeamSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return ResponseEntity.ok(ApiResponse.ok(teamService.listTeams(search, pageable)));
    }

    @GetMapping("/cards")
    public ResponseEntity<ApiResponse<List<TeamSummaryResponse>>> cards() {
        return ResponseEntity.ok(ApiResponse.ok(teamService.listAllForCards()));
    }

    // NEW — for task create modal + filters
    @GetMapping("/dropdown")
    public ResponseEntity<ApiResponse<List<TeamSummaryResponse>>> dropdown() {
        return ResponseEntity.ok(ApiResponse.ok(teamService.listForDropdown()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        access.requireAdmin(userId);
        teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<ApiResponse<List<Long>>> members(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(teamService.listTeamMemberIds(id)));
    }
    // ─── PHASE 2: member management ──────────────────────────

    @GetMapping("/{id}/members/details")
    public ResponseEntity<ApiResponse<List<TeamMemberResponse>>> memberDetails(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanViewTeam(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(teamService.listTeamMembers(id)));
    }

    @GetMapping("/{id}/members/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> myTeamAccess(
            @PathVariable Long id, @CurrentUserId Long userId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canManage", access.canManageTeam(userId, id));
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddTeamMemberRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageTeam(userId, id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Member added", teamService.addMember(id, req, userId)));
    }

    @PutMapping("/{id}/members/{employeeId}")
    public ResponseEntity<ApiResponse<TeamMemberResponse>> updateMember(
            @PathVariable Long id,
            @PathVariable Long employeeId,
            @Valid @RequestBody UpdateTeamMemberRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageTeam(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(teamService.updateMember(id, employeeId, req)));
    }

    @DeleteMapping("/{id}/members/{employeeId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable Long id,
            @PathVariable Long employeeId,
            @CurrentUserId Long userId) {
        access.requireCanManageTeam(userId, id);
        teamService.removeMember(id, employeeId);
        return ResponseEntity.ok(ApiResponse.ok("Removed", null));
    }
    /**
     * Rich team dashboard endpoint.
     * Returns: team meta + members + tasks (both explicit + member-derived).
     */
    @GetMapping("/{id}/tasks")
    public ResponseEntity<ApiResponse<Map<String, Object>>> tasks(
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int limit,
            @CurrentUserId Long userId) {
        access.requireCanViewTeam(userId, id);

        TeamService.TeamTasksResult r = teamService.listTeamTasks(id, limit);

        Map<String, Object> team = new LinkedHashMap<>();
        team.put("id", r.teamId());
        team.put("name", r.teamName());
        team.put("leadId", r.teamLeadId());
        team.put("leadName", r.teamLeadName());
        team.put("memberIds", r.memberIds());

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("openTasks", r.openTasks());
        stats.put("doneTasks", r.doneTasks());
        stats.put("totalTasks", r.openTasks() + r.doneTasks());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("team", team);
        body.put("stats", stats);
        body.put("tasks", r.tasks());
        return ResponseEntity.ok(ApiResponse.ok(body));
    }
}