package com.mentis.hrms.pm.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.model.Holiday;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.entity.Milestone;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.entity.TaskDependency;
import com.mentis.hrms.pm.repository.MilestoneRepository;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskDependencyRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.repository.TeamMemberRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectPhaseService;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.HolidayRepository;
import com.mentis.hrms.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController("pmProjectTimelineController")
@RequestMapping("/api/pm/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectTimelineController {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final MilestoneRepository milestoneRepository;
    private final ProjectPhaseService projectPhaseService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PmAccessService access;

    /** One feed for the Gantt and the calendar. */
    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<Map<String, Object>>> timeline(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);

        // ── tasks ──
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (Task t : taskRepository.findByProjectId(projectId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("status", t.getStatus() == null ? null : t.getStatus().name());
            m.put("startDate", t.getStartDate());
            m.put("dueDate", t.getDueDate());
            m.put("baselineStart", t.getBaselineStart());
            m.put("baselineDue", t.getBaselineDue());
            m.put("progressPct", t.getProgressPct());
            m.put("parentTaskId", t.getParentTaskId());
            m.put("dependsOn", taskDependencyRepository.findByTaskId(t.getId()).stream()
                    .map(TaskDependency::getDependsOnTaskId).toList());
            tasks.add(m);
        }

        // ── milestones ──
        List<Map<String, Object>> milestones = new ArrayList<>();
        for (Milestone ms : milestoneRepository.findByProjectIdOrderByDueDateAsc(projectId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", ms.getId());
            m.put("name", ms.getName());
            m.put("dueDate", ms.getDueDate());
            m.put("status", ms.getStatus() == null ? null : ms.getStatus().name());
            milestones.add(m);
        }

        LocalDate from = LocalDate.now().minusDays(90);
        LocalDate to = LocalDate.now().plusDays(365);

        // ── holidays (public information) ──
        List<Map<String, Object>> holidays = new ArrayList<>();
        for (Holiday h : holidayRepository.findByHolidayDateBetween(from, to)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", h.getHolidayDate());
            m.put("name", h.getHolidayName());
            m.put("type", h.getHolidayType());
            holidays.add(m);
        }

        // ── approved leave: name + dates only (no type, no reason) ──
        List<Map<String, Object>> leaves = new ArrayList<>();
        if (access.canViewProjectPeople(userId, projectId)) {
            Set<Long> pks = new LinkedHashSet<>();
            projectRepository.findById(projectId).ifPresent(p -> {
                if (p.getOwnerId() != null) pks.add(p.getOwnerId());
                if (p.getTeamId() != null)
                    teamMemberRepository.findByTeamId(p.getTeamId()).forEach(tm -> pks.add(tm.getEmployeeId()));
            });
            projectMemberRepository.findByProjectId(projectId).forEach(m -> pks.add(m.getEmployeeId()));

            for (Long pk : pks) {
                Employee e = employeeRepository.findById(pk).orElse(null);
                if (e == null || e.getEmployeeId() == null) continue;
                String name = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                        + (e.getLastName() == null ? "" : e.getLastName())).trim();
                if (name.isEmpty()) name = "Employee #" + pk;
                for (LeaveRequest l : leaveRequestRepository
                        .findApprovedLeavesForEmployeeInRange(e.getEmployeeId(), from, to)) {
                    if (Boolean.TRUE.equals(l.getWithdrawn())) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("employeeId", pk);
                    m.put("name", name);
                    m.put("startDate", l.getStartDate());
                    m.put("endDate", l.getEndDate());
                    leaves.add(m);
                }
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tasks", tasks);
        out.put("milestones", milestones);
        out.put("phases", projectPhaseService.listByProject(projectId));
        out.put("holidays", holidays);
        out.put("leaves", leaves);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }
}