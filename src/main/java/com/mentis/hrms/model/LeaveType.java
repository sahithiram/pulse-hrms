package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "leave_type")
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "paid", nullable = false)
    private boolean paid = true;

    @Column(name = "requires_document_after_days")
    private Integer requiresDocumentAfterDays;

    @Column(name = "max_consecutive_days")
    private Integer maxConsecutiveDays;

    @Column(name = "min_notice_days", nullable = false)
    private int minNoticeDays = 0;

    @Column(name = "allow_half_day", nullable = false)
    private boolean allowHalfDay = true;

    @Column(name = "allow_negative_balance", nullable = false)
    private boolean allowNegativeBalance = false;

    @Column(name = "gender_restriction", length = 16)
    private String genderRestriction;

    @Column(name = "applicable_employment_types", length = 64)
    private String applicableEmploymentTypes;

    @Column(name = "applicable_after_days_of_service")
    private Integer applicableAfterDaysOfService;

    @Column(name = "counts_weekends_and_holidays", nullable = false)
    private boolean countsWeekendsAndHolidays = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public Integer getRequiresDocumentAfterDays() { return requiresDocumentAfterDays; }
    public void setRequiresDocumentAfterDays(Integer requiresDocumentAfterDays) { this.requiresDocumentAfterDays = requiresDocumentAfterDays; }
    public Integer getMaxConsecutiveDays() { return maxConsecutiveDays; }
    public void setMaxConsecutiveDays(Integer maxConsecutiveDays) { this.maxConsecutiveDays = maxConsecutiveDays; }
    public int getMinNoticeDays() { return minNoticeDays; }
    public void setMinNoticeDays(int minNoticeDays) { this.minNoticeDays = minNoticeDays; }
    public boolean isAllowHalfDay() { return allowHalfDay; }
    public void setAllowHalfDay(boolean allowHalfDay) { this.allowHalfDay = allowHalfDay; }
    public boolean isAllowNegativeBalance() { return allowNegativeBalance; }
    public void setAllowNegativeBalance(boolean allowNegativeBalance) { this.allowNegativeBalance = allowNegativeBalance; }
    public String getGenderRestriction() { return genderRestriction; }
    public void setGenderRestriction(String genderRestriction) { this.genderRestriction = genderRestriction; }
    public String getApplicableEmploymentTypes() { return applicableEmploymentTypes; }
    public void setApplicableEmploymentTypes(String applicableEmploymentTypes) { this.applicableEmploymentTypes = applicableEmploymentTypes; }
    public Integer getApplicableAfterDaysOfService() { return applicableAfterDaysOfService; }
    public void setApplicableAfterDaysOfService(Integer applicableAfterDaysOfService) { this.applicableAfterDaysOfService = applicableAfterDaysOfService; }
    public boolean isCountsWeekendsAndHolidays() { return countsWeekendsAndHolidays; }
    public void setCountsWeekendsAndHolidays(boolean countsWeekendsAndHolidays) { this.countsWeekendsAndHolidays = countsWeekendsAndHolidays; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
