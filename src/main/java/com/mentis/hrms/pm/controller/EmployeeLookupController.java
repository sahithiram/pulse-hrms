package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController("pmEmployeeLookupController")
@RequestMapping("/api/pm/employees")
@RequiredArgsConstructor
public class EmployeeLookupController {

    private final EmployeeRepository employeeRepository;
    private final PmAccessService access;

    @GetMapping("/dropdown")
    public ApiResponse<List<Map<String, Object>>> dropdown(@CurrentUserId Long userId) {
        // C0: only admins, PMs, or anyone with at least one managed project sees the directory
        if (!access.isAdmin(userId) && !access.isPmRole(userId)
                && access.managedProjectIds(userId).isEmpty()) {
            return ApiResponse.ok(List.of());
        }
        var employees = employeeRepository.findAll();
        List<Map<String, Object>> list = new ArrayList<>();
        int limit = Math.min(employees.size(), 200);

        for (int i = 0; i < limit; i++) {
            Object emp = employees.get(i);
            Long id = extractLong(emp, "getId");
            String name = extractName(emp);
            if (id == null) continue;

            String role = extractString(emp, "getDesignation");
            if (role == null || role.isBlank()) role = extractString(emp, "getRole");
            if (role == null) role = "";

            String dept = extractString(emp, "getDepartment");
            if (dept == null) dept = "";

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", id);
            row.put("name", name);
            row.put("role", role);
            row.put("department", dept);
            list.add(row);
        }
        return ApiResponse.ok(list);
    }

    private Long extractLong(Object obj, String getter) {
        try {
            Object v = obj.getClass().getMethod(getter).invoke(obj);
            if (v instanceof Long) return (Long) v;
            if (v instanceof Integer) return ((Integer) v).longValue();
            if (v instanceof Number) return ((Number) v).longValue();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractString(Object obj, String getter) {
        try {
            Object v = obj.getClass().getMethod(getter).invoke(obj);
            return v == null ? null : String.valueOf(v);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractName(Object emp) {
        // Try common getters in priority order
        String[] singleGetters = {
                "getEmployeeName", "getFullName", "getName", "getDisplayName"
        };
        for (String g : singleGetters) {
            String v = extractString(emp, g);
            if (v != null && !v.isBlank()) return v;
        }
        // Try firstName + lastName
        String first = extractString(emp, "getFirstName");
        String last = extractString(emp, "getLastName");
        if ((first != null && !first.isBlank()) || (last != null && !last.isBlank())) {
            return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        }
        // Try email
        String email = extractString(emp, "getEmail");
        if (email != null && !email.isBlank()) return email;
        // Fallback to ID
        Long id = extractLong(emp, "getId");
        return id != null ? "Employee #" + id : "Unknown";
    }
}