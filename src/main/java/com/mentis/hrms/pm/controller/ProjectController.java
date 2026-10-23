package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.dto.response.ProjectResponse;
import com.mentis.hrms.pm.dto.response.ProjectSummaryResponse;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.MyWorkService;
import com.mentis.hrms.pm.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mentis.hrms.pm.dto.response.ProjectFinanceResponse;
import com.mentis.hrms.pm.service.ProjectFinanceService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/pm/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final MyWorkService myWorkService;
    private final PmAccessService access;
    private final ProjectFinanceService financeService;

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> create(
            @Valid @RequestBody CreateProjectRequest req,
            @CurrentUserId Long userId) {
        access.requireCanCreateProject(userId);
        ProjectResponse res = projectService.createProject(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Project created", res));
    }

    @GetMapping("/me/projects")
    public ResponseEntity<ApiResponse<List<ProjectSummaryResponse>>> myProjects(
            @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(myWorkService.listMyProjects(userId)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest req,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);

        // Owner change is admin-only
        if (req.getOwnerId() != null && !access.isAdmin(userId)) {
            ProjectResponse current = projectService.getProject(id);
            if (!req.getOwnerId().equals(current.getOwnerId()))
                throw new PmAccessDeniedException("Only admins can change the project owner.");
        }

        // Step 3a — refuse changes that would silently invalidate an existing baseline
        guardBaselinedChanges(id, req);

        return ResponseEntity.ok(ApiResponse.ok(projectService.updateProject(id, req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(projectService.getProject(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProjectSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction,
            @CurrentUserId Long userId) {

        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);
        PageResponse<ProjectSummaryResponse> result = access.isAdmin(userId)
                ? projectService.listProjects(search, status, ownerId, pageable)
                : projectService.listVisibleProjects(search, status, ownerId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<ProjectResponse>> archive(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Archived", projectService.archiveProject(id, userId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        if (!access.isAdmin(userId) && !access.isProjectOwner(userId, id))
            throw new PmAccessDeniedException("Only an admin or the project owner can delete a project.");
        projectService.deleteProject(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
    // ─── PHASE 5: baseline, cost, health ───────────────────────

    @PostMapping("/{id}/baseline")
    public ResponseEntity<ApiResponse<ProjectResponse>> baseline(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);

        // Step 3a — don't let a re-baseline silently overwrite a project that is already running.
        ProjectResponse current = projectService.getProject(id);
        ProjectStatus st = current.getStatus();
        if (st != null && st != ProjectStatus.PLANNING && st != ProjectStatus.ACTIVE) {
            throw new BusinessRuleException(
                    "A baseline can only be set while a project is Planning or Active. Current status: " + st + ".");
        }

        return ResponseEntity.ok(ApiResponse.ok("Baseline saved", projectService.saveBaseline(id, userId)));
    }

    /** Money data: managers only. */
    @GetMapping("/{id}/finance")
    public ResponseEntity<ApiResponse<ProjectFinanceResponse>> finance(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(financeService.finance(id)));
    }

    @PostMapping("/{id}/health/recompute")
    public ResponseEntity<ApiResponse<ProjectFinanceResponse>> recomputeOne(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Recomputed", financeService.recalculateProject(id, userId)));
    }

    @PostMapping("/{id}/health/auto")
    public ResponseEntity<ApiResponse<ProjectFinanceResponse>> backToAuto(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Health is automatic again", financeService.resetToAuto(id, userId)));
    }

    /** The list page's "Recompute" button: every project the caller can manage. */
    @PostMapping("/health/recompute")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> recomputeAll(@CurrentUserId Long userId) {
        int[] r = financeService.recomputeAllFor(pid -> access.canManageProject(userId, pid), userId);
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("checked", r[0]);
        out.put("changed", r[1]);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Send the status update to the owner and managers right now. */
    @PostMapping("/{id}/status-report")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> statusReport(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanManageProject(userId, id);
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("notified", financeService.sendStatusReport(id, true));
        return ResponseEntity.ok(ApiResponse.ok("Status update sent", out));
    }

    // ────────────────────────────────────────────────────────────
    // Step 3a — baseline guard.
    // Refuse budget / date changes on a project that already has
    // a baseline AND is past PLANNING, so the baseline table stays
    // meaningful. A re-baseline is the explicit way to move it.
    // ────────────────────────────────────────────────────────────
    private void guardBaselinedChanges(Long projectId, UpdateProjectRequest req) {
        ProjectResponse current = projectService.getProject(projectId);
        if (current == null) return;

        ProjectStatus st = current.getStatus();
        if (st == null || st == ProjectStatus.PLANNING || st == ProjectStatus.COMPLETED || st == ProjectStatus.ARCHIVED) {
            return;
        }

        // ProjectResponse exposes baselinedAt (Phase 5) — that's the "a baseline exists" signal.
        boolean hasBaseline = current.getBaselinedAt() != null;
        if (!hasBaseline) return;

        boolean budgetChanged = req.getBudgetTotal() != null
                && !Objects.equals(req.getBudgetTotal(), current.getBudgetTotal());
        boolean startChanged = req.getStartDate() != null
                && !Objects.equals(req.getStartDate(), current.getStartDate());
        boolean endChanged = req.getEndDate() != null
                && !Objects.equals(req.getEndDate(), current.getEndDate());

        if (budgetChanged || startChanged || endChanged) {
            throw new BusinessRuleException(
                    "This project already has a baseline. Use 'Re-baseline' in Health & budget to move the dates or the budget.");
        }
    }
}