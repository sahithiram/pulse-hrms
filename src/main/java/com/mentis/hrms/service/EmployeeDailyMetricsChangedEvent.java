package com.mentis.hrms.service;

import java.time.LocalDate;

/** Published AFTER a metrics row is (re)computed — Sprint 6/9 consume (notifications, dashboards, jobs). */
public class EmployeeDailyMetricsChangedEvent {
    public final String employeeId;
    public final LocalDate date;
    public final String status;

    public EmployeeDailyMetricsChangedEvent(String employeeId, LocalDate date, String status) {
        this.employeeId = employeeId;
        this.date = date;
        this.status = status;
    }
}
