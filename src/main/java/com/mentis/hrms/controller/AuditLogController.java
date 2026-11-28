package com.mentis.hrms.controller;

import com.mentis.hrms.model.AuditLog;
import com.mentis.hrms.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Read-only audit viewer. SUPER_ADMIN/HR_MANAGER/HR (enforced again at interceptor). */
@RestController
@RequestMapping("/api/audit-log")
public class AuditLogController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<Page<AuditLog>> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size,
                                               @RequestParam(required = false) String action) {
        PageRequest pr = PageRequest.of(page, Math.min(size, 200));
        if (action != null && !action.isBlank()) {
            LocalDateTime from = LocalDate.now().minusDays(30).atStartOfDay();
            return ResponseEntity.ok(auditLogRepository.findByActionAndOccurredAtBetween(action, from, LocalDateTime.now(), pr));
        }
        LocalDateTime from = LocalDate.now().minusDays(7).atStartOfDay();
        return ResponseEntity.ok(auditLogRepository.findByOccurredAtBetween(from, LocalDateTime.now(), pr));
    }
}
