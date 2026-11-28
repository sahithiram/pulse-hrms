package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.mentis.hrms.model.enums.MigrationIssueStatus;

@Entity
@Table(name = "data_migration_issues")
public class DataMigrationIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;   // DEPARTMENT | DESIGNATION | LOCATION | MANAGER | SALARY | EMAIL

    @Column(name = "employee_ref", nullable = false, length = 100)
    private String employeeRef;  // business key (employee_id)

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "raw_value", length = 500)
    private String rawValue;

    @Column(name = "issue", nullable = false, length = 255)
    private String issue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MigrationIssueStatus status = MigrationIssueStatus.OPEN;

    @Column(name = "resolution", length = 500)
    private String resolution;

    @Column(name = "checksum", nullable = false, length = 64, unique = true)
    private String checksum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEmployeeRef() { return employeeRef; }
    public void setEmployeeRef(String employeeRef) { this.employeeRef = employeeRef; }
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getRawValue() { return rawValue; }
    public void setRawValue(String rawValue) { this.rawValue = rawValue; }
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }
    public MigrationIssueStatus getStatus() { return status; }
    public void setStatus(MigrationIssueStatus status) { this.status = status; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
}
