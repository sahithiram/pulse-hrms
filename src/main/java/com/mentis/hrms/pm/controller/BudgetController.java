package com.mentis.hrms.pm.controller;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.TaskTimeLog;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskTimeLogRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectMemberRates;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PM-007 / PM-010 — the real Budget API that the Home "Budget" tab and the /pm/09-budget page call.
 *
 * Every number here comes from the database:
 *   budgetTotal / budgetSpent  -> pm_projects
 *   cost of logged time        -> pm_task_time_logs  x  pm_project_members.hourly_rate
 * Nothing is hardcoded, and an empty database returns zeros (never demo figures).
 *
 * Rules (unchanged from ReportController):
 *   all projects  -> Super Admin only
 *   one project   -> owner / MANAGER / Super Admin (the finance tier)
 */
@RestController("pmBudgetController")
@RequestMapping("/api/pm/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRates memberView;
    private final TaskTimeLogRepository timeLogRepository;
    private final EmployeeRepository employeeRepository;
    private final PmAccessService access;

    /* ───────────────────────── scope ───────────────────────── */

    /**
     * Company-wide view: any finance-tier user (Super Admin, PM, project owner/manager).
     * Single-project view: still the finance tier for *that* project.
     * Previously the aggregate demanded SUPER_ADMIN, which is why a working PM login got a 403
     * and the budget page sat on "Loading budget data…".
     */
    private void requireFinance(Long userId, Long projectId) {
        if (projectId == null) access.requireCanViewFinanceAnywhere(userId);
        else access.requireCanViewFinance(userId, projectId);
    }

    /** the projects the caller is allowed to see in a company-wide aggregate */
    private List<Project> projectsInScope(Long userId) {
        if (access.isSuperAdmin(userId)) return projectRepository.findAll();
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        // projects where the caller is owner / co-manager / MANAGER member
        ids.addAll(access.managedProjectIds(userId));
        // a PM who created a project still sees it, even without a membership row yet
        if (access.isPmRole(userId)) {
            projectRepository.findAll().stream()
                    .filter(p -> Objects.equals(p.getCreatedBy(), userId))
                    .forEach(p -> ids.add(p.getId()));
        }
        if (ids.isEmpty()) return List.of();
        return projectRepository.findAllById(ids);
    }

    /* ───────────────────────── endpoints ───────────────────────── */

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long projectId) {
        requireFinance(userId, projectId);
        List<Project> projects = (projectId != null)
                ? projectRepository.findById(projectId).map(List::of).orElse(List.of())
                : projectsInScope(userId);

        BigDecimal budget = BigDecimal.ZERO, spent = BigDecimal.ZERO;
        int overrun = 0, atRisk = 0;
        for (Project p : projects) {
            BigDecimal b = nz(p.getBudgetTotal());
            BigDecimal s = nz(p.getBudgetSpent());
            budget = budget.add(b);
            spent = spent.add(s);
            if (b.signum() > 0 && s.compareTo(b) > 0) overrun++;
            if (b.signum() > 0 && s.compareTo(b.multiply(BigDecimal.valueOf(0.85))) > 0
                    && s.compareTo(b) <= 0) atRisk++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("totalBudget", budget);
        out.put("totalSpent", spent);
        out.put("remaining", budget.subtract(spent));
        out.put("overrunCount", overrun);
        out.put("projectsAtRisk", atRisk);
        out.put("projectCount", projects.size());
        out.put("currency", "INR");
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> projects(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(defaultValue = "50") int limit) {
        requireFinance(userId, projectId);
        List<Project> projects = (projectId != null)
                ? projectRepository.findById(projectId).map(List::of).orElse(List.of())
                : projectsInScope(userId);

        Map<Long, String> ownerNames = ownerNames(projects);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Project p : projects) {
            if (rows.size() >= limit) break;
            BigDecimal b = nz(p.getBudgetTotal());
            BigDecimal s = nz(p.getBudgetSpent());
            int used = b.signum() == 0 ? 0
                    : s.multiply(BigDecimal.valueOf(100)).divide(b, 0, RoundingMode.HALF_UP).intValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("code", p.getCode());
            row.put("name", p.getName());
            row.put("status", p.getStatus() != null ? p.getStatus().name() : "PLANNING");
            row.put("ownerId", p.getOwnerId());
            row.put("ownerName", ownerNames.get(p.getOwnerId()));
            row.put("budget", b);
            row.put("spent", s);
            row.put("remaining", b.subtract(s));
            row.put("utilPct", used);
            row.put("health", used > 100 ? "OVER" : used > 85 ? "AT_RISK" : "ON_TRACK");
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> -((Integer) r.get("utilPct"))));
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    /** { labels[], budget[], spent[] } — the shape Chart.js wants on the Home page */
    @GetMapping("/charts/project-budget")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> chartProjectBudget(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "8") int limit) {
        requireFinance(userId, null);
        List<Project> projects = projectsInScope(userId).stream()
                .sorted(Comparator.comparing(p -> -nz(p.getBudgetTotal()).doubleValue()))
                .limit(limit).collect(Collectors.toList());
        List<String> labels = new ArrayList<>();
        List<BigDecimal> budget = new ArrayList<>();
        List<BigDecimal> spent = new ArrayList<>();
        for (Project p : projects) {
            labels.add(p.getName());
            budget.add(nz(p.getBudgetTotal()));
            spent.add(nz(p.getBudgetSpent()));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("labels", labels);
        out.put("budget", budget);
        out.put("spent", spent);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /**
     * Cost split by the department of the person who logged the time.
     * amount = logged minutes / 60 x hourly_rate of that person on that project (default rate 0).
     */
    @GetMapping("/categories")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> categories(
            @CurrentUserId Long userId,
            @RequestParam(required = false) Long projectId) {
        requireFinance(userId, projectId);
        List<Project> projects = (projectId != null)
                ? projectRepository.findById(projectId).map(List::of).orElse(List.of())
                : projectsInScope(userId);
        Set<Long> projectIds = projects.stream().map(Project::getId).collect(Collectors.toSet());
        if (projectIds.isEmpty()) return ResponseEntity.ok(ApiResponse.ok(List.of()));

        List<TaskTimeLog> logs = new ArrayList<>();
        for (Long pid : projectIds) logs.addAll(timeLogRepository.findForProject(pid));
        Map<Long, String> deptOf = new HashMap<>();
        for (Employee e : employeeRepository.findAllById(
                logs.stream().map(TaskTimeLog::getEmployeeId).filter(Objects::nonNull).distinct().toList())) {
            deptOf.put(e.getId(), e.getDepartment() == null || e.getDepartment().isBlank()
                    ? "Unassigned" : e.getDepartment());
        }

        Map<String, BigDecimal> byDept = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (TaskTimeLog l : logs) {
            String dept = deptOf.getOrDefault(l.getEmployeeId(), "Unassigned");
            BigDecimal cost = memberView.cost(l.getEmployeeId(), projectIds, l.getMinutes());
            byDept.merge(dept, cost, BigDecimal::add);
            total = total.add(cost);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byDept.entrySet()) {
            int pct = total.signum() == 0 ? 0
                    : e.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", e.getKey());
            row.put("amount", e.getValue());
            row.put("pct", pct);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(r -> -((BigDecimal) r.get("amount")).doubleValue()));
        return ResponseEntity.ok(ApiResponse.ok(rows));
    }

    /** everything /pm/09-budget needs in one call */
    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(@CurrentUserId Long userId) {
        requireFinance(userId, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("summary", nullableData(summary(userId, null)));
        out.put("projects", nullableData(projects(userId, null, 50)));
        out.put("categories", nullableData(categories(userId, null)));
        out.put("chart", nullableData(chartProjectBudget(userId, 8)));
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /* ───────────────────────── helpers ───────────────────────── */

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private static <T> T nullableData(ResponseEntity<ApiResponse<T>> r) {
        return r == null || r.getBody() == null ? null : r.getBody().getData();
    }

    private Map<Long, String> ownerNames(List<Project> projects) {
        List<Long> ids = projects.stream().map(Project::getOwnerId).filter(Objects::nonNull).distinct().toList();
        return employeeRepository.findAllById(ids).stream().collect(Collectors.toMap(
                Employee::getId,
                e -> {
                    String n = ((e.getFirstName() == null ? "" : e.getFirstName()) + " "
                            + (e.getLastName() == null ? "" : e.getLastName())).trim();
                    return n.isEmpty() ? (e.getEmail() == null ? "Employee #" + e.getId() : e.getEmail()) : n;
                },
                (a, b) -> a));
    }

}
