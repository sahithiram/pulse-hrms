package com.mentis.hrms.service;

/** Domain event — published AFTER the approving transaction commits (Sprint 6 notification bus consumes these). */
public class LeaveApprovedEvent {
    public final String requestId;
    public final String employeeId;
    public final String leaveTypeCode;
    public final java.time.LocalDate startDate;
    public final java.time.LocalDate endDate;
    public final String approverId;

    public LeaveApprovedEvent(String requestId, String employeeId, String leaveTypeCode,
                              java.time.LocalDate startDate, java.time.LocalDate endDate, String approverId) {
        this.requestId = requestId;
        this.employeeId = employeeId;
        this.leaveTypeCode = leaveTypeCode;
        this.startDate = startDate;
        this.endDate = endDate;
        this.approverId = approverId;
    }
}
