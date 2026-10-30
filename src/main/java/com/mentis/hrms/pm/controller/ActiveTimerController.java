package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.ActiveTimerService;
import com.mentis.hrms.pm.service.ActiveTimerService.StopResult;
import com.mentis.hrms.pm.service.ActiveTimerService.TimerView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController("pmActiveTimerController")
@RequestMapping("/api/pm/timer")
@RequiredArgsConstructor
public class ActiveTimerController {

    public record StartBody(Long taskId, String note, Boolean billable) {}
    public record StopBody(Boolean confirm) {}

    private final ActiveTimerService service;

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<TimerView>> current(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.current(userId)));
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<TimerView>> start(@RequestBody StartBody b, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Timer started", service.start(b.taskId(), b.note(), b.billable(), userId)));
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse<StopResult>> stop(@RequestBody(required = false) StopBody b, @CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(service.stop(b != null && Boolean.TRUE.equals(b.confirm()), userId)));
    }

    @PostMapping("/discard")
    public ResponseEntity<ApiResponse<Void>> discard(@CurrentUserId Long userId) {
        service.discard(userId);
        return ResponseEntity.ok(ApiResponse.ok("Timer discarded", null));
    }
}