
package com.mentis.hrms.workperformance.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * NEW entity — kept separate from com.mentis.hrms.model.Employee so that
 * the existing Employee entity/table is never modified.
 * One row per employee, linking them to their ClickUp member id.
 */
@Entity
@Table(name = "employee_clickup_mapping")
public class EmployeeClickUpMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(name = "clickup_user_id", nullable = false)
    private String clickupUserId;

    @Column(name = "clickup_username")
    private String clickupUsername;

    @Column(name = "active")
    private Boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public EmployeeClickUpMapping() {}

    public EmployeeClickUpMapping(String employeeId, String clickupUserId) {
        this.employeeId = employeeId;
        this.clickupUserId = clickupUserId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // ===== Getters & Setters =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getClickupUserId() { return clickupUserId; }
    public void setClickupUserId(String clickupUserId) { this.clickupUserId = clickupUserId; }

    public String getClickupUsername() { return clickupUsername; }
    public void setClickupUsername(String clickupUsername) { this.clickupUsername = clickupUsername; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
