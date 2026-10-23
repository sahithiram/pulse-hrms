package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AssignTaskRequest {
    @NotEmpty
    private List<Long> employeeIds;
}