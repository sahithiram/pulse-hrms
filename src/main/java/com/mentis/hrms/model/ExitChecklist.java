package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exit_checklist",
       uniqueConstraints = @UniqueConstraint(name = "uq_exit", columnNames = {"employee_id", "task"}))
public class ExitChecklist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 50)
    private String employeeId;

    @Column(name = "task", nullable = false, length = 48)
    private String task;

    @Column(name = "owner", nullable = false, length = 30)
    private String owner;

    @Column(name = "status", nullable = false, length = 12)
    private String status = "PENDING";   // PENDING|DONE|NA

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "completed_by", length = 50)
    private String completedBy;

    @Column(name = "notes", length = 255)
    private String notes;

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getTask() { return task; }
    public void setTask(String task) { this.task = task; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
