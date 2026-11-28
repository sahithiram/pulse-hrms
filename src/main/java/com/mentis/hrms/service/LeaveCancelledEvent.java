package com.mentis.hrms.service;

/** Domain event — published AFTER a cancellation is applied (Sprint 6 notification bus consumes these). */
public class LeaveCancelledEvent {
    public final String requestId;
    public final String employeeId;
    public final boolean partial;
    public final java.util.List<java.time.LocalDate> releasedDates;

    public LeaveCancelledEvent(String requestId, String employeeId, boolean partial,
                               java.util.List<java.time.LocalDate> releasedDates) {
        this.requestId = requestId;
        this.employeeId = employeeId;
        this.partial = partial;
        this.releasedDates = releasedDates;
    }
}
