package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.mentis.hrms.model.enums.EmploymentMode;
import com.mentis.hrms.model.enums.WorkMode;

@Entity
@Table(name = "employee_job_history")
public class EmployeeJobHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "designation_id")
    private Long designationId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "manager_employee_id")
    private Long managerEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_mode", length = 20)
    private EmploymentMode employmentMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", length = 20)
    private WorkMode workMode;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo; // NULL = current snapshot

    @Column(name = "change_reason", nullable = false, length = 255)
    private String changeReason;

    @Column(name = "changed_by")
    private Long changedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public Long getDesignationId() { return designationId; }
    public void setDesignationId(Long designationId) { this.designationId = designationId; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public Long getManagerEmployeeId() { return managerEmployeeId; }
    public void setManagerEmployeeId(Long managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
    public EmploymentMode getEmploymentMode() { return employmentMode; }
    public void setEmploymentMode(EmploymentMode employmentMode) { this.employmentMode = employmentMode; }
    public WorkMode getWorkMode() { return workMode; }
    public void setWorkMode(WorkMode workMode) { this.workMode = workMode; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
