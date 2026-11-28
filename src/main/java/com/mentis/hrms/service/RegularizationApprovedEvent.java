package com.mentis.hrms.service;

import java.time.LocalDate;

public class RegularizationApprovedEvent {
    public final Long regularizationId;
    public final String employeeId;
    public final LocalDate date;
    public final String approver;

    public RegularizationApprovedEvent(Long regularizationId, String employeeId, LocalDate date, String approver) {
        this.regularizationId = regularizationId;
        this.employeeId = employeeId;
        this.date = date;
        this.approver = approver;
    }
}
