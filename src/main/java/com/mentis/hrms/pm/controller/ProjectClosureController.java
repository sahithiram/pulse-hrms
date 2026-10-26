package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.ClosureItemRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ClosureItemResponse;
import com.mentis.hrms.pm.dto.response.ClosureStatusResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.ProjectClosureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Closure checklist. Every successful write also triggers the global realtime ping (PmCurrentUserInterceptor). */
@RestController("pmProjectClosureController")
@RequestMapping("/api/pm/projects/{projectId}")
@RequiredArgsConstructor
public class ProjectClosureController {

    private final ProjectClosureService service;
    private final PmAccessService access;

    @GetMapping("/closure-items")
    public ResponseEntity<ApiResponse<List<ClosureItemResponse>>> list(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId); // read, leave
        return ResponseEntity.ok(ApiResponse.ok(service.list(projectId)));
    }

    @GetMapping("/closure-status")
    public ResponseEntity<ApiResponse<ClosureStatusResponse>> status(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId); // read, leave
        return ResponseEntity.ok(ApiResponse.ok(service.status(projectId)));
    }

    @PostMapping("/closure-items")
    public ResponseEntity<ApiResponse<ClosureItemResponse>> create(
            @PathVariable Long projectId, @Valid @RequestBody ClosureItemRequest req,
            @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, projectId); // ✅ CHANGED
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Item added", service.add(projectId, req.getTitle(), userId)));
    }

    @PostMapping("/closure-items/defaults")
    public ResponseEntity<ApiResponse<List<ClosureItemResponse>>> defaults(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, projectId); // ✅ CHANGED
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Standard checklist added", service.seedDefaults(projectId, userId)));
    }

    @PutMapping("/closure-items/{itemId}")
    public ResponseEntity<ApiResponse<ClosureItemResponse>> update(
            @PathVariable Long projectId, @PathVariable Long itemId,
            @Valid @RequestBody ClosureItemRequest req, @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, projectId); // ✅ CHANGED
        return ResponseEntity.ok(ApiResponse.ok(service.update(projectId, itemId, req, userId)));
    }

    @DeleteMapping("/closure-items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long projectId, @PathVariable Long itemId, @CurrentUserId Long userId) {
        access.requireCanAdminister(userId, projectId); // ✅ CHANGED
        service.delete(projectId, itemId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Item removed", null));
    }
}