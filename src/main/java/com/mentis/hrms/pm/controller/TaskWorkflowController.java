package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.pm.service.TaskWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController("pmTaskWorkflowController")
@RequestMapping("/api/pm/workflow")
@RequiredArgsConstructor
public class TaskWorkflowController {

    private final PmAccessService access;

    /**
     * Without taskId: the plain table (old behaviour, nothing changes for existing callers).
     * With taskId: the moves THIS user may make on THIS task (STEP 4: hides Done for assignees
     * on projects that require review). The server still enforces the rule on every change.
     */
    @GetMapping("/task-transitions")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> taskTransitions(
            @RequestParam(required = false) Long taskId,
            @CurrentUserId Long userId) {
        boolean gate = false;
        if (taskId != null) {
            access.requireCanView(userId, taskId);
            gate = access.isReviewGateActive(userId, taskId);
        }
        return ResponseEntity.ok(ApiResponse.ok(TaskWorkflow.asMap(gate)));
    }
}