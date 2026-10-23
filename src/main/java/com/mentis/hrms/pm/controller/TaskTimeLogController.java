package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.LogTimeRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.TimeLogResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskTimeLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("pmTaskTimeLogController")
@RequestMapping("/api/pm/tasks")
@RequiredArgsConstructor
public class TaskTimeLogController {

    private final TaskTimeLogService service;
    private final PmAccessService access;

    @PostMapping("/{taskId}/time")
    public ResponseEntity<ApiResponse<TimeLogResponse>> log(
            @PathVariable Long taskId,
            @Valid @RequestBody LogTimeRequest req,
            @CurrentUserId Long userId) {
        access.requireCanEdit(userId, taskId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Time logged", service.log(taskId, req, userId)));
    }

    @GetMapping("/{taskId}/time")
    public ResponseEntity<ApiResponse<List<TimeLogResponse>>> list(
            @PathVariable Long taskId, @CurrentUserId Long userId) {
        access.requireCanView(userId, taskId);
        return ResponseEntity.ok(ApiResponse.ok(service.list(taskId)));
    }

    @DeleteMapping("/time/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, @CurrentUserId Long userId) {
        service.delete(id, userId);   // ownership is checked inside the service
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}