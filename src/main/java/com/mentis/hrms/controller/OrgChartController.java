package com.mentis.hrms.controller;

import com.mentis.hrms.dto.HistoryEntryDTO;
import com.mentis.hrms.dto.OrgChartNodeDTO;
import com.mentis.hrms.dto.EmployeeSummaryDTO;
import com.mentis.hrms.service.OrgTreeService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Org read APIs. Session-authenticated (same mechanism as RoleInterceptor).\n * No object-level authorisation here yet — that is Sprint 2 (AccessPolicy).\n * Salary history is structurally present but masked until Sprint 2 lands: we return\n * amount ONLY when the session role is SUPER_ADMIN/HR_MANAGER (defense-in-depth).
 */
@RestController
@RequestMapping("/api")
public class OrgChartController {

    private final OrgTreeService orgTreeService;

    @Autowired
    public OrgChartController(OrgTreeService orgTreeService) {
        this.orgTreeService = orgTreeService;
    }

    @GetMapping("/employees/{id}/team")
    public ResponseEntity<List<EmployeeSummaryDTO>> team(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("userId") == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(orgTreeService.getDirectReports(id));
    }

    @GetMapping("/org/chart")
    public ResponseEntity<OrgChartNodeDTO> chart(HttpSession session) {
        if (session.getAttribute("userId") == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(orgTreeService.getOrgChart());
    }

    @GetMapping("/employees/{id}/history")
    public ResponseEntity<List<HistoryEntryDTO>> history(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("userId") == null) return ResponseEntity.status(401).build();
        String role = (String) session.getAttribute("userRole");
        boolean salaryAllowed = "SUPER_ADMIN".equals(role) || "HR_MANAGER".equals(role);
        List<HistoryEntryDTO> history = orgTreeService.getHistory(id);
        if (!salaryAllowed) {
            history = history.stream()
                    .map(h -> "SALARY".equals(h.type())
                            ? new HistoryEntryDTO("SALARY", "salary change (masked)", null, null,
                                h.effectiveFrom(), h.effectiveTo(), h.reason(), h.changedBy())
                            : h)
                    .toList();
        }
        return ResponseEntity.ok(history);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
