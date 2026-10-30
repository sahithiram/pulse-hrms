package com.mentis.hrms.pm.dto.response;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TimeLogResponse {
    private Long id;
    private Long taskId;
    private Long employeeId;
    private String employeeName;
    private LocalDate logDate;
    private Integer minutes;
    private String note;
    private LocalDateTime createdAt;
    private Boolean billable;
    private Boolean overtime;
}