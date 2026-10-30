package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.BalanceApplyRequest;
import com.mentis.hrms.pm.dto.request.BalancePreviewRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.BalanceApplyResponse;
import com.mentis.hrms.pm.dto.response.BalancePreviewResponse;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.WorkloadBalanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * GAP 19c. Two steps, never one: preview (writes nothing) then apply (only what was ticked).
 * Fine-grained rights are checked per task inside the service; here we only keep out people
 * who could not even open the workload view.
 */
@RestController("pmWorkloadBalanceController")
@RequestMapping("/api/pm/workload/balance")
@RequiredArgsConstructor
public class WorkloadBalanceController {

    private final WorkloadBalanceService service;
    private final PmAccessService access;

    /** same rule as GET /api/pm/workload/team/{id} */
    private void requireTeamViewer(Long userId, Long teamId) {
        if (!access.isAdmin(userId) && !access.isPmRole(userId) && !access.isTeamLead(userId, teamId))
            throw new PmAccessDeniedException("Only admins, project managers or the team lead can balance this workload.");
    }

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<BalancePreviewResponse>> preview(
            @Valid @RequestBody BalancePreviewRequest req, @CurrentUserId Long userId) {
        if ((req.getTeamId() == null) == (req.getProjectId() == null))
            throw new BusinessRuleException("Provide either a team or a project, not both");
        if (req.getTeamId() != null) requireTeamViewer(userId, req.getTeamId());
        else access.requireCanViewProjectPeople(userId, req.getProjectId());
        return ResponseEntity.ok(ApiResponse.ok(service.preview(req, userId)));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<BalanceApplyResponse>> apply(
            @Valid @RequestBody BalanceApplyRequest req, @CurrentUserId Long userId) {
        BalanceApplyResponse res = service.apply(req, userId);
        return ResponseEntity.ok(ApiResponse.ok(res.getApplied() + " task(s) reassigned", res));
    }
}