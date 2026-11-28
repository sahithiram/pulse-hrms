package com.mentis.hrms.dto;

public record EmployeeSummaryDTO(
        Long id,
        String employeeId,
        String fullName,
        String email,
        String designationName,
        String departmentName,
        String employmentStatus,
        String workMode) {}
