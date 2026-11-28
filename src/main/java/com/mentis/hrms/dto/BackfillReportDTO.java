package com.mentis.hrms.dto;

import java.util.List;

public record BackfillReportDTO(
        int employeesScanned,
        int departmentMapped,
        int designationMapped,
        int locationMapped,
        int managerMapped,
        int salaryParsed,
        int issuesCreated,
        List<String> issueSummaries) {}
