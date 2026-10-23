package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.model.Notification;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.repository.EmployeeRepository;
import com.mentis.hrms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController("pmNotificationController")
@RequestMapping("/api/pm/notifications")
@RequiredArgsConstructor
public class PmNotificationController {

    private final NotificationRepository repo;
    private final EmployeeRepository employeeRepository;

    /** Get the current employee's employeeId string (SA001, etc.). */
    private String codeOf(Long pk) {
        return employeeRepository.findById(pk)
                .map(e -> e.getEmployeeId()).orElse(null);
    }

    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", n.getId());
        m.put("type", n.getType());
        m.put("action", n.getAction());
        m.put("title", n.getTitle());
        m.put("message", n.getMessage());
        m.put("taskId", n.getTaskId());
        m.put("projectId", n.getProjectId());
        m.put("actorId", n.getActorId());
        m.put("sender", n.getSender());
        m.put("priority", n.getPriority());
        m.put("deepLink", n.getDeepLink());
        m.put("createdAt", n.getCreatedAt());
        m.put("read", n.getReadAt() != null);
        return m;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list(
            @CurrentUserId Long userId,
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "30") int limit) {
        String code = codeOf(userId);
        if (code == null) return ResponseEntity.ok(ApiResponse.ok(List.of()));

        List<Notification> all = "all".equalsIgnoreCase(filter) || filter == null
                ? repo.findRecent(code, "EMPLOYEE", PageRequest.of(0, limit))
                : repo.findByRecipientIdAndRecipientTypeAndActionOrderByCreatedAtDesc(
                code, "EMPLOYEE", filter);

        return ResponseEntity.ok(ApiResponse.ok(
                all.stream().map(this::toDto).collect(Collectors.toList())));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Object>>> unread(@CurrentUserId Long userId) {
        String code = codeOf(userId);
        long n = code == null ? 0 : repo.countByRecipientIdAndRecipientTypeAndReadAtIsNull(code, "EMPLOYEE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", n);
        return ResponseEntity.ok(ApiResponse.ok(m));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable Long id, @CurrentUserId Long userId) {
        String code = codeOf(userId);
        if (code == null) return ResponseEntity.ok(ApiResponse.ok("ok", null));
        repo.findByIdAndRecipientIdAndRecipientType(id, code, "EMPLOYEE").ifPresent(n -> {
            n.setReadAt(LocalDateTime.now());
            repo.save(n);
        });
        return ResponseEntity.ok(ApiResponse.ok("Marked read", null));
    }

    @PostMapping("/mark-all-read")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> markAll(@CurrentUserId Long userId) {
        String code = codeOf(userId);
        if (code != null) repo.markAllAsRead(code, "EMPLOYEE");
        return ResponseEntity.ok(ApiResponse.ok("All read", null));
    }
}