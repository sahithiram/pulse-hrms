package com.mentis.hrms.dto;

import java.util.List;

public record OrgChartNodeDTO(
        Long id,
        String employeeId,
        String fullName,
        String designationName,
        String departmentName,
        String employmentStatus,
        boolean active,
        List<OrgChartNodeDTO> reports) {}
