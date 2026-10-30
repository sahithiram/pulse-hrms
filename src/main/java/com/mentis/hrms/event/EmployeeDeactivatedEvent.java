package com.mentis.hrms.event;

/**
 * Published after an employee is deactivated or deleted.
 * Handled in the PM module so EmployeeService does not depend on PM types.
 */
public class EmployeeDeactivatedEvent {

    private final Long employeePk;
    private final String employeeName;

    public EmployeeDeactivatedEvent(Long employeePk, String employeeName) {
        this.employeePk = employeePk;
        this.employeeName = employeeName;
    }

    public Long getEmployeePk() { return employeePk; }

    public String getEmployeeName() { return employeeName; }
}
