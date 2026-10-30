package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.enums.ProjectRole;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectMemberResponse {
    private Long id;
    private Long projectId;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private ProjectRole role;
    private Integer allocationPct;
    private Integer utilizationPct;
    private BigDecimal hourlyRate;     // null unless the caller can manage the project
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean owner;
    private LocalDateTime createdAt;
}