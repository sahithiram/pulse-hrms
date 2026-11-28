package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Per-day storage for a leave request — read by attendance/performance/daily metrics. */
@Entity
@Table(name = "leave_request_day",
       uniqueConstraints = @UniqueConstraint(name = "uq_lrd_day", columnNames = {"request_id", "day"}))
public class LeaveRequestDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, length = 50)
    private String requestId;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "day_fraction", nullable = false, precision = 3, scale = 2)
    private BigDecimal dayFraction = BigDecimal.ONE;

    @Column(name = "counted", nullable = false)
    private boolean counted = true;

    public Long getId() { return id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDate getDay() { return day; }
    public void setDay(LocalDate day) { this.day = day; }
    public BigDecimal getDayFraction() { return dayFraction; }
    public void setDayFraction(BigDecimal dayFraction) { this.dayFraction = dayFraction; }
    public boolean isCounted() { return counted; }
    public void setCounted(boolean counted) { this.counted = counted; }
}
