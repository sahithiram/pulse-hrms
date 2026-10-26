package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateApprovalRequest;
import com.mentis.hrms.pm.dto.request.DecisionRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ApprovalResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmApprovalController")
@RequestMapping("/api/pm/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService service;
    private final PmAccessService access;

    @PostMapping
    public ResponseEntity<ApiResponse<ApprovalResponse>> create(
            @Valid @RequestBody CreateApprovalRequest req,
            @CurrentUserId Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Request created", service.create(req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ApprovalResponse>> get(
            @PathVariable Long id, @CurrentUserId Long userId) {
        ApprovalResponse a = service.get(id);
        boolean mine = userId.equals(a.getRequestedBy()) || userId.equals(a.getApproverId());
        if (!mine && !access.isSuperAdmin(userId)) // ✅ CHANGED (was isAdmin)
            throw new PmAccessDeniedException("This approval is not yours.");
        return ResponseEntity.ok(ApiResponse.ok(a));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ApprovalResponse>>> mine(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.listMine(userId)));
    }

    @GetMapping("/requested")
    public ResponseEntity<ApiResponse<List<ApprovalResponse>>> requested(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.listRequested(userId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ApprovalResponse>>> all(
            @RequestParam(required = false) String status,
            @CurrentUserId Long userId) {
        access.requireSuperAdmin(userId); // ✅ CHANGED (was requireAdmin)
        return ResponseEntity.ok(ApiResponse.ok(service.listAll(status)));
    }

    @PostMapping("/{id}/decide")
    public ResponseEntity<ApiResponse<ApprovalResponse>> decide(
            @PathVariable Long id,
            @Valid @RequestBody DecisionRequest req,
            @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Decision recorded", service.decide(id, req, userId)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long id, @CurrentUserId Long userId) {
        service.cancel(id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Cancelled", null));
    }
}