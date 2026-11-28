package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "optional_holiday_choice",
       uniqueConstraints = @UniqueConstraint(name = "uq_ohc_employee_holiday",
               columnNames = {"employee_id", "holiday_id"}))
public class OptionalHolidayChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 32)
    private String employeeId;

    @Column(name = "holiday_id", nullable = false)
    private Long holidayId;

    /** CHOSEN|REJECTED */
    @Column(name = "status", nullable = false, length = 12)
    private String status = "CHOSEN";

    @Column(name = "chosen_at")
    private LocalDateTime chosenAt;

    public OptionalHolidayChoice() { this.chosenAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public Long getHolidayId() { return holidayId; }
    public void setHolidayId(Long holidayId) { this.holidayId = holidayId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getChosenAt() { return chosenAt; }
    public void setChosenAt(LocalDateTime chosenAt) { this.chosenAt = chosenAt; }
}
