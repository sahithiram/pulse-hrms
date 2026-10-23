package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.entity.ProjectBaselineHistory;
import com.mentis.hrms.pm.repository.ProjectBaselineHistoryRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only history of replaced baselines. New URL only; no existing path changes. */
@RestController("pmProjectBaselineController")
@RequestMapping("/api/pm/projects/{projectId}/baselines")
@RequiredArgsConstructor
public class ProjectBaselineController {

    private final ProjectBaselineHistoryRepository repository;
    private final PmAccessService access;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectBaselineHistory>>> history(
            @PathVariable Long projectId, @CurrentUserId Long userId) {
        access.requireCanViewProject(userId, projectId);
        return ResponseEntity.ok(ApiResponse.ok(repository.findByProjectIdOrderByVersionNoDesc(projectId)));
    }
}