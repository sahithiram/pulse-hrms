package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "offer_revision",
       uniqueConstraints = @UniqueConstraint(name = "uq_rev", columnNames = {"offer_letter_id", "version"}))
public class OfferRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_letter_id", nullable = false)
    private Long offerLetterId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "designation", length = 120)
    private String designation;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "annual_salary", length = 64)
    private String annualSalary;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "joining_date", length = 32)
    private String joiningDate;

    @Column(name = "employment_type", length = 32)
    private String employmentType;

    @Column(name = "change_reason", nullable = false, length = 255)
    private String changeReason;

    @Column(name = "revised_by", length = 50)
    private String revisedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getOfferLetterId() { return offerLetterId; }
    public void setOfferLetterId(Long offerLetterId) { this.offerLetterId = offerLetterId; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getAnnualSalary() { return annualSalary; }
    public void setAnnualSalary(String annualSalary) { this.annualSalary = annualSalary; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getJoiningDate() { return joiningDate; }
    public void setJoiningDate(String joiningDate) { this.joiningDate = joiningDate; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getRevisedBy() { return revisedBy; }
    public void setRevisedBy(String revisedBy) { this.revisedBy = revisedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
