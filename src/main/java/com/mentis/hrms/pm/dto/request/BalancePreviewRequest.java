package com.mentis.hrms.pm.dto.request;

import lombok.*;

import java.time.LocalDate;

/** Exactly one of teamId / projectId. from / to default to the current Monday-Sunday week. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BalancePreviewRequest {
    private Long teamId;
    private Long projectId;
    private LocalDate from;
    private LocalDate to;
}