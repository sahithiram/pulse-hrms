package com.mentis.hrms.pm.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record BaselineHistoryResponse(Integer versionNo, LocalDate baselineStart, LocalDate baselineEnd,
                                      BigDecimal baselineBudget, LocalDateTime baselinedAt, Long baselinedBy,
                                      LocalDateTime supersededAt, Long changeRequestId, String reason) {}