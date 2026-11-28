package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "roster_entry",
       uniqueConstraints = @UniqueConstraint(name = "uq_re_emp_date", columnNames = {"employee_id", "roster_date"}))
public class RosterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "roster_date", nullable = false)
    private LocalDate rosterDate;

    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public LocalDate getRosterDate() { return rosterDate; }
    public void setRosterDate(LocalDate rosterDate) { this.rosterDate = rosterDate; }
    public Long getShiftId() { return shiftId; }
    public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
}
