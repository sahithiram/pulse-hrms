package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.scheduler.TaskDueReminderScheduler;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Profile("dev")
@RestController
@RequestMapping("/api/pm/dev")
@RequiredArgsConstructor
public class DevReminderController {
    private final TaskDueReminderScheduler scheduler;
    private final PmAccessService access;

    @PostMapping("/run-reminders")
    public ResponseEntity<ApiResponse<Integer>> run(@CurrentUserId Long userId) {
        access.requireSuperAdmin(userId); // STEP F5
        return ResponseEntity.ok(ApiResponse.ok(scheduler.runFor(java.time.LocalDate.now())));
    }
}