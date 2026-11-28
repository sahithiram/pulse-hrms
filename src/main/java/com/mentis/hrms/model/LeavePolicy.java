package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "leave_policy")
public class LeavePolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "leave_type_id", nullable = false)
    private Long leaveTypeId;

    @Column(name = "department", length = 64)
    private String department;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "employment_type", length = 32)
    private String employmentType;

    @Column(name = "grade", length = 32)
    private String grade;

    @Column(name = "annual_quota", nullable = false, precision = 5, scale = 2)
    private BigDecimal annualQuota = BigDecimal.ZERO;

    @Column(name = "accrual_method", nullable = false, length = 16)
    private String accrualMethod = "NONE"; // NONE|MONTHLY|QUARTERLY|YEARLY_UPFRONT

    @Column(name = "accrual_day")
    private Integer accrualDay;

    @Column(name = "carry_forward_limit", precision = 5, scale = 2)
    private BigDecimal carryForwardLimit = BigDecimal.ZERO;

    @Column(name = "carry_forward_expiry_months")
    private int carryForwardExpiryMonths = 0;

    @Column(name = "encashable", nullable = false)
    private boolean encashable = false;

    @Column(name = "encashment_cap", precision = 5, scale = 2)
    private BigDecimal encashmentCap;

    @Column(name = "probation_rule", nullable = false, length = 16)
    private String probationRule = "FULL"; // PRORATA|NONE|FULL

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLeaveTypeId() { return leaveTypeId; }
    public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public Long getLocationId() { return locationId; }
    public void setLocationId(Long locationId) { this.locationId = locationId; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public BigDecimal getAnnualQuota() { return annualQuota; }
    public void setAnnualQuota(BigDecimal annualQuota) { this.annualQuota = annualQuota; }
    public String getAccrualMethod() { return accrualMethod; }
    public void setAccrualMethod(String accrualMethod) { this.accrualMethod = accrualMethod; }
    public Integer getAccrualDay() { return accrualDay; }
    public void setAccrualDay(Integer accrualDay) { this.accrualDay = accrualDay; }
    public BigDecimal getCarryForwardLimit() { return carryForwardLimit; }
    public void setCarryForwardLimit(BigDecimal carryForwardLimit) { this.carryForwardLimit = carryForwardLimit; }
    public int getCarryForwardExpiryMonths() { return carryForwardExpiryMonths; }
    public void setCarryForwardExpiryMonths(int carryForwardExpiryMonths) { this.carryForwardExpiryMonths = carryForwardExpiryMonths; }
    public boolean isEncashable() { return encashable; }
    public void setEncashable(boolean encashable) { this.encashable = encashable; }
    public BigDecimal getEncashmentCap() { return encashmentCap; }
    public void setEncashmentCap(BigDecimal encashmentCap) { this.encashmentCap = encashmentCap; }
    public String getProbationRule() { return probationRule; }
    public void setProbationRule(String probationRule) { this.probationRule = probationRule; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }

    public boolean isEffectiveOn(LocalDate date) {
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) return false;
        if (effectiveTo != null && date.isAfter(effectiveTo)) return false;
        return true;
    }
}
