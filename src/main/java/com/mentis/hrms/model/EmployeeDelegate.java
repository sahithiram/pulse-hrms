package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.mentis.hrms.model.enums.DelegateScope;

@Entity
@Table(name = "employee_delegate")
public class EmployeeDelegate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "delegator_id", nullable = false)
    private Long delegatorId;

    @Column(name = "delegate_id", nullable = false)
    private Long delegateId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 30)
    private DelegateScope scope;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public Long getDelegatorId() { return delegatorId; }
    public void setDelegatorId(Long delegatorId) { this.delegatorId = delegatorId; }
    public Long getDelegateId() { return delegateId; }
    public void setDelegateId(Long delegateId) { this.delegateId = delegateId; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public DelegateScope getScope() { return scope; }
    public void setScope(DelegateScope scope) { this.scope = scope; }
    public boolean isActiveOn(LocalDate date) { return !date.isBefore(fromDate) && !date.isAfter(toDate); }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
