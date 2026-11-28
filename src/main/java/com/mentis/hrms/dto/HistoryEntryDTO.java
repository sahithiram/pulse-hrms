package com.mentis.hrms.dto;

import java.time.LocalDate;

public record HistoryEntryDTO(
        String type,            // JOB | SALARY
        String detail,          // human readable summary, e.g. "Engineering → Platform"
        java.math.BigDecimal salaryAmount,
        String currency,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String reason,
        Long changedBy) {}
