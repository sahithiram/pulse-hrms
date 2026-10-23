package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectPriority;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything the person chooses when creating a project from a template. All optional except the name. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FromTemplateRequest {
    private String name;               // falls back to template.project.name
    private String code;
    private String description;
    private Long ownerId;              // default: the caller
    private Long departmentId;
    private Long clientId;
    private Long teamId;
    private LocalDate startDate;       // anchor for every offset; default: today
    private LocalDate endDate;         // default: the last milestone/sprint of the template
    private ProjectPriority priority;  // default: template.project.priority, else MEDIUM
    private BigDecimal budgetTotal;
    private List<Long> memberIds;
}