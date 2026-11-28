package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "exit_interview")
public class ExitInterview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, unique = true, length = 50)
    private String employeeId;

    @Column(name = "form_json", columnDefinition = "TEXT")
    private String formJson;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    public String getFormJson() { return formJson; }
    public void setFormJson(String formJson) { this.formJson = formJson; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
