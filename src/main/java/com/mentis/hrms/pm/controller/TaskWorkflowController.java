package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.TaskWorkflow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController("pmTaskWorkflowController")
@RequestMapping("/api/pm/workflow")
public class TaskWorkflowController {

    @GetMapping("/task-transitions")
    public ResponseEntity<ApiResponse<Map<String, List<String>>>> taskTransitions(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(TaskWorkflow.asMap()));
    }
}