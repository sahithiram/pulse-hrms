package com.mentis.hrms.pm.dto.request;

import com.mentis.hrms.pm.enums.ProjectPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProjectRequest {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 40)
    private String code;

    private String description;

    @NotNull
    private Long ownerId;

    private Long departmentId;
    private Long clientId;

    @NotNull
    private ProjectPriority priority;

    private LocalDate startDate;
    private LocalDate endDate;
    private Long teamId;
    private BigDecimal budgetTotal;
    /** Optional. Null means the default (ON). */
    private Boolean reviewRequired;
    /** People chosen in the "People" step of the create form (added as MEMBER). */
    private List<Long> memberIds;
}