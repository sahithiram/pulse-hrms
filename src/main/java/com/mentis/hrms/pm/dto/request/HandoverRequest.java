package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter @Setter
public class HandoverRequest {
    @NotNull private Long fromEmployeeId;
    @NotNull private Long toEmployeeId;
    /** null = every project (employee resigning) */
    private Long projectId;
    /** null or empty = all open tasks */
    private List<Long> taskIds;
    /** only used when projectId is set */
    private boolean removeFromProject;
    private String reason;
}