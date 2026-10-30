package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.ConvertRetroItemRequest;
import com.mentis.hrms.pm.dto.request.RetroItemRequest;
import com.mentis.hrms.pm.dto.response.*;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.RetroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmRetroController")
@RequestMapping("/api/pm")
@RequiredArgsConstructor
public class RetroController {

    private final RetroService service;

    @GetMapping("/sprints/{sprintId}/retro")
    public ResponseEntity<ApiResponse<RetroResponse>> get(@PathVariable Long sprintId, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(sprintId, userId)));
    }

    @PostMapping("/sprints/{sprintId}/retro/items")
    public ResponseEntity<ApiResponse<RetroResponse.Item>> add(
            @PathVariable Long sprintId, @Valid @RequestBody RetroItemRequest req, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Added", service.addItem(sprintId, req, userId)));
    }

    @PutMapping("/sprints/{sprintId}/retro/items/{itemId}")
    public ResponseEntity<ApiResponse<RetroResponse.Item>> update(
            @PathVariable Long sprintId, @PathVariable Long itemId,
            @Valid @RequestBody RetroItemRequest req, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Updated", service.updateItem(sprintId, itemId, req, userId)));
    }

    @DeleteMapping("/sprints/{sprintId}/retro/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long sprintId, @PathVariable Long itemId, @CurrentUserId Long userId) {
        service.deleteItem(sprintId, itemId, userId);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    @PostMapping("/sprints/{sprintId}/retro/items/{itemId}/convert")
    public ResponseEntity<ApiResponse<TaskResponse>> convert(
            @PathVariable Long sprintId, @PathVariable Long itemId,
            @RequestBody(required = false) ConvertRetroItemRequest req, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Task created", service.convert(sprintId, itemId, req, userId)));
    }

    @PostMapping("/sprints/{sprintId}/retro/close")
    public ResponseEntity<ApiResponse<RetroResponse>> close(@PathVariable Long sprintId, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Closed", service.close(sprintId, userId)));
    }

    @PostMapping("/sprints/{sprintId}/retro/reopen")
    public ResponseEntity<ApiResponse<RetroResponse>> reopen(@PathVariable Long sprintId, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Reopened", service.reopen(sprintId, userId)));
    }

    @GetMapping("/projects/{projectId}/retros")
    public ResponseEntity<ApiResponse<List<RetroSummaryResponse>>> history(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.history(projectId, userId)));
    }
}