package com.mentis.hrms.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

public class EmployeeDailyMetricsId implements Serializable {

    private String employeeId;
    private LocalDate metricsDate;

    public EmployeeDailyMetricsId() {}

    public EmployeeDailyMetricsId(String employeeId, LocalDate metricsDate) {
        this.employeeId = employeeId;
        this.metricsDate = metricsDate;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public LocalDate getMetricsDate() { return metricsDate; }
    public void setMetricsDate(LocalDate metricsDate) { this.metricsDate = metricsDate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmployeeDailyMetricsId other)) return false;
        return Objects.equals(employeeId, other.employeeId) && Objects.equals(metricsDate, other.metricsDate);
    }

    @Override
    public int hashCode() { return Objects.hash(employeeId, metricsDate); }
}
