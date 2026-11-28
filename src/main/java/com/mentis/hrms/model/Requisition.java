package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "requisition")
public class Requisition {
    public static final String DRAFT = "DRAFT";
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String OPEN = "OPEN";
    public static final String ON_HOLD = "ON_HOLD";
    public static final String CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "req_code", nullable = false, unique = true, length = 24)
    private String reqCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "headcount", nullable = false)
    private int headcount = 1;

    @Column(name = "budget_min", precision = 15, scale = 2)
    private BigDecimal budgetMin;

    @Column(name = "budget_max", precision = 15, scale = 2)
    private BigDecimal budgetMax;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "hiring_manager", length = 50)
    private String hiringManager;

    @Column(name = "status", nullable = false, length = 20)
    private String status = DRAFT;

    @Column(name = "closed_reason", length = 255)
    private String closedReason;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "approved_by", length = 50)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    public Long getId() { return id; }
    public String getReqCode() { return reqCode; }
    public void setReqCode(String reqCode) { this.reqCode = reqCode; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getHeadcount() { return headcount; }
    public void setHeadcount(int headcount) { this.headcount = headcount; }
    public BigDecimal getBudgetMin() { return budgetMin; }
    public void setBudgetMin(BigDecimal budgetMin) { this.budgetMin = budgetMin; }
    public BigDecimal getBudgetMax() { return budgetMax; }
    public void setBudgetMax(BigDecimal budgetMax) { this.budgetMax = budgetMax; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getHiringManager() { return hiringManager; }
    public void setHiringManager(String hiringManager) { this.hiringManager = hiringManager; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClosedReason() { return closedReason; }
    public void setClosedReason(String closedReason) { this.closedReason = closedReason; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
}
