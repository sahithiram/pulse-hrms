package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateProjectRequest;
import com.mentis.hrms.pm.dto.request.UpdateProjectRequest;
import com.mentis.hrms.pm.dto.response.*;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.security.ProjectFinanceMasker;
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
import com.mentis.hrms.pm.service.ProjectFinanceService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import com.mentis.hrms.pm.dto.request.ReopenProjectRequest;
@RestController
@RequestMapping("/api/pm/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final MyWorkService myWorkService;
    private final PmAccessService access;
    private final ProjectFinanceService financeService;
    private final ProjectFinanceMasker masker; // STEP 1b

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
        return ResponseEntity.ok(ApiResponse.ok(
                masker.maskAll(myWorkService.listMyProjects(userId), userId))); // STEP 1b
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest req,
            @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, id);

        // Owner change is super-admin-only
        if (req.getOwnerId() != null && !access.isSuperAdmin(userId)) {
            ProjectResponse current = projectService.getProject(id);
            if (!req.getOwnerId().equals(current.getOwnerId()))
                throw new PmAccessDeniedException("Only a Super Admin can change the project owner.");
        }

        // Step 3a: refuse changes that would silently invalidate an existing baseline
        guardBaselinedChanges(id, req);

        return ResponseEntity.ok(ApiResponse.ok(projectService.updateProject(id, req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> get(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, id);
        // STEP 1b: budget fields are hidden unless the viewer can administer this project
        return ResponseEntity.ok(ApiResponse.ok(masker.mask(projectService.getProject(id), userId)));
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
        // leave as-is: HR still sees all projects (access.isAdmin)
        PageResponse<ProjectSummaryResponse> result = access.isAdmin(userId)
                ? projectService.listProjects(search, status, ownerId, pageable)
                : projectService.listVisibleProjects(search, status, ownerId, userId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(masker.maskPage(result, userId))); // STEP 1b
    }

    @PatchMapping("/{id}/archive")
    public ResponseEntity<ApiResponse<ProjectResponse>> archive(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Archived", projectService.archiveProject(id, userId)));
    }
    /** STEP 8: only a Super Admin may reopen a completed project, and must give a reason. */
    @PostMapping("/{id}/reopen")
    public ResponseEntity<ApiResponse<ProjectResponse>> reopen(
            @PathVariable Long id,
            @Valid @RequestBody ReopenProjectRequest req,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId);
        return ResponseEntity.ok(ApiResponse.ok("Project reopened",
                masker.mask(projectService.reopenProject(id, req.getReason(), userId), userId)));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @CurrentUserId Long userId) {
        if (!access.isSuperAdmin(userId) && !access.isProjectOwner(userId, id))
            throw new PmAccessDeniedException("Only a Super Admin or the project owner can delete a project.");
        projectService.deleteProject(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    // ─── PHASE 5: baseline, cost, health ───────────────────────

    @PostMapping("/{id}/baseline")
    public ResponseEntity<ApiResponse<ProjectResponse>> baseline(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, id);

        // Step 3a (revised): a baseline is locked ONLY once the project is COMPLETED or
        // ARCHIVED. AT_RISK, DELAYED, ON_HOLD and ACTIVE projects need re-baselining the
        // most, so they are allowed. The service already blocks writes to locked projects.
        ProjectResponse current = projectService.getProject(id);
        ProjectStatus st = current.getStatus();
        if (st == ProjectStatus.COMPLETED || st == ProjectStatus.ARCHIVED) {
            throw new BusinessRuleException("This project is completed or archived, so its baseline is locked.");
        }

        return ResponseEntity.ok(ApiResponse.ok("Baseline saved", projectService.saveBaseline(id, userId, reason)));
    }

    /** Money data: owner, managers and Super Admin only. */
    @GetMapping("/{id}/finance")
    public ResponseEntity<ApiResponse<ProjectFinanceResponse>> finance(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanViewFinance(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(financeService.finance(id)));
    }

    @PostMapping("/{id}/health/recompute")
    public ResponseEntity<ApiResponse<ProjectFinanceResponse>> recomputeOne(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Recomputed", financeService.recalculateProject(id, userId)));
    }

    @PostMapping("/{id}/health/auto")
    public ResponseEntity<ApiResponse<ProjectFinanceResponse>> backToAuto(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, id);
        return ResponseEntity.ok(ApiResponse.ok("Health is automatic again", financeService.resetToAuto(id, userId)));
    }

    /** The list page's "Recompute" button: every project the caller can administer. */
    @PostMapping("/health/recompute")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> recomputeAll(@CurrentUserId Long userId) {
        int[] r = financeService.recomputeAllFor(pid -> access.canAdministerProject(userId, pid), userId);
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("checked", r[0]);
        out.put("changed", r[1]);
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /** Send the status update to the owner and managers right now. */
    @PostMapping("/{id}/status-report")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> statusReport(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, id);
        Map<String, Integer> out = new LinkedHashMap<>();
        out.put("notified", financeService.sendStatusReport(id, true));
        return ResponseEntity.ok(ApiResponse.ok("Status update sent", out));
    }
    @GetMapping("/{id}/baselines")
    public ResponseEntity<ApiResponse<List<com.mentis.hrms.pm.dto.response.BaselineHistoryResponse>>> baselines(
            @PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanViewFinance(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(projectService.baselineHistory(id)));
    }

    // ────────────────────────────────────────────────────────────
    // Step 3a: baseline guard.
    // Refuse budget / date changes on a project that already has
    // a baseline AND is in a state where the baseline is meant to be
    // authoritative (ON_HOLD, AT_RISK, DELAYED). PLANNING has no
    // baseline yet; COMPLETED and ARCHIVED are already read-only;
    // ACTIVE is explicitly the window in which the plan can still
    // be adjusted before it hardens.
    // (Uses the UNMASKED service response on purpose.)
    // ────────────────────────────────────────────────────────────
    private void guardBaselinedChanges(Long projectId, UpdateProjectRequest req) {
        ProjectResponse current = projectService.getProject(projectId);
        if (current == null) return;

        ProjectStatus st = current.getStatus();
        if (st == null
                || st == ProjectStatus.PLANNING
                || st == ProjectStatus.ACTIVE
                || st == ProjectStatus.COMPLETED
                || st == ProjectStatus.ARCHIVED) {
            return;
        }

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