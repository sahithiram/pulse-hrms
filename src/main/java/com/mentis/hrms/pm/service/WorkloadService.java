package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.response.WorkloadResponse;

import java.time.LocalDate;
import java.util.List;

public interface WorkloadService {
    WorkloadResponse forEmployee(Long employeePk, LocalDate from, LocalDate to);
    List<WorkloadResponse> forTeam(Long teamId, LocalDate from, LocalDate to);
    List<WorkloadResponse> forProject(Long projectId, LocalDate from, LocalDate to);
    List<WorkloadResponse> forEmployees(List<Long> employeePks, LocalDate from, LocalDate to);

    /** Returns null if OK, else a human message about why this assignment is risky. */
    String capacityWarning(Long employeePk, LocalDate from, LocalDate to);
}