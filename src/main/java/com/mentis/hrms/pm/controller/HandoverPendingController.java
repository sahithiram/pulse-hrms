package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.PmHandoverFinder;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController("pmHandoverPendingController")
@RequestMapping("/api/pm/handover")
@RequiredArgsConstructor
public class HandoverPendingController {

    public record PendingRow(Long employeeId, String name, LocalDate lastWorkingDay,
                             int openTasks, int ownedProjects, int ledTeams) {}
    public record Candidate(Long id, String name) {}

    private final PmHandoverFinder finder;
    private final EmployeeRepository employeeRepo;
    private final PmAccessService access;

    private boolean privileged(Long userId) { return access.isAdmin(userId) || access.isHr(userId); }   // ADAPT isHr

    /** Admin/HR see everything; a manager sees only the parts in projects they administer. */
    private PmHandoverFinder.Detail visible(PmHandoverFinder.Detail d, Long userId) {
        if (d == null || privileged(userId)) return d;
        var tasks = d.tasks().stream().filter(t -> access.canAdministerProject(userId, t.projectId())).collect(Collectors.toList());
        var projects = d.ownedProjects().stream().filter(p -> access.canAdministerProject(userId, p.id())).collect(Collectors.toList());
        var teams = access.canManageTeamLeads(userId) ? d.ledTeams() : List.<PmHandoverFinder.Ref>of();        // ADAPT
        return new PmHandoverFinder.Detail(d.employeeId(), d.name(), d.lastWorkingDay(), tasks, projects, teams);
    }

    private boolean empty(PmHandoverFinder.Detail d) {
        return d.tasks().isEmpty() && d.ownedProjects().isEmpty() && d.ledTeams().isEmpty();
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<PendingRow>>> pending(@CurrentUserId Long userId) {
        List<PendingRow> out = new ArrayList<>();
        for (var e : employeeRepo.findByStatusIn(List.of("RESIGNED", "NOTICE_PERIOD"))) {       // ADAPT repo method + values
            var d = visible(finder.detail(e.getId()), userId);
            if (d == null || empty(d)) continue;
            out.add(new PendingRow(d.employeeId(), d.name(), d.lastWorkingDay(),
                    d.tasks().size(), d.ownedProjects().size(), d.ledTeams().size()));
        }
        out.sort(Comparator.comparing(PendingRow::lastWorkingDay, Comparator.nullsLast(Comparator.naturalOrder())));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/pending/{employeeId}")
    public ResponseEntity<ApiResponse<PmHandoverFinder.Detail>> detail(
            @PathVariable Long employeeId, @CurrentUserId Long userId) {
        var d = visible(finder.detail(employeeId), userId);
        if (d == null) throw new ResourceNotFoundException("Employee not found: " + employeeId);
        if (!privileged(userId) && empty(d)) throw new PmAccessDeniedException("You have nothing to hand over for this employee.");
        return ResponseEntity.ok(ApiResponse.ok(d));
    }

    /** Active employees for the pickers on the handover and team-lead pages. */
    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<List<Candidate>>> candidates(@CurrentUserId Long userId) {
        if (!privileged(userId) && !access.canManageTeamLeads(userId) && !access.isAnyProjectManager(userId))   // ADAPT both
            throw new PmAccessDeniedException("Not allowed.");
        List<Candidate> out = employeeRepo.findAll().stream()
                .filter(e -> "ACTIVE".equals(e.getStatus()))                                                    // ADAPT
                .map(e -> new Candidate(e.getId(),
                        ((e.getFirstName() == null ? "" : e.getFirstName()) + " " + (e.getLastName() == null ? "" : e.getLastName())).trim()))
                .sorted(Comparator.comparing(Candidate::name, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }
}