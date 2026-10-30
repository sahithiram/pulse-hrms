package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.scheduler.DigestResult;
import com.mentis.hrms.pm.scheduler.ProjectWeeklyDigestScheduler;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController("pmProjectDigestController")
@RequestMapping("/api/pm/digest")
@RequiredArgsConstructor
public class ProjectDigestController {

    private final ProjectWeeklyDigestScheduler scheduler;
    private final PmAccessService access;


    @PostMapping("/run-now")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> runNow(@CurrentUserId Long userId) {
        if (!access.isSuperAdmin(userId))
            throw new PmAccessDeniedException("Only a Super Admin can trigger the digest.");
        DigestResult r = scheduler.runNow();
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        out.put("sent", r.sent());
        out.put("skipped", r.skipped());
        out.put("failed", r.failed());
        out.put("emailsSent", r.sent());                              // old key kept: no response shape removed
        return ResponseEntity.ok(ApiResponse.ok(out));
    }
}