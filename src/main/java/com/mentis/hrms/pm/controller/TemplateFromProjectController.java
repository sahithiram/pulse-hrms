package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.TemplateFromProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("pmTemplateFromProjectController")
@RequestMapping("/api/pm/templates")
@RequiredArgsConstructor
public class TemplateFromProjectController {

    public record Body(String name, String description) {}

    private final TemplateFromProjectService service;

    @PostMapping("/from-project/{projectId}")
    public ResponseEntity<ApiResponse<Object>> fromProject(
            @PathVariable Long projectId, @RequestBody Body b, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Template saved",
                service.save(projectId, b == null ? null : b.name(), b == null ? null : b.description(), userId)));
    }
}