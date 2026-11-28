package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "hrms_report_saved_view",
       uniqueConstraints = @UniqueConstraint(name = "uq_hrsv", columnNames = {"user_id", "report_key", "name"}))
public class SavedReportView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 50)
    private String userId;

    @Column(name = "report_key", nullable = false, length = 64)
    private String reportKey;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "filters_json", nullable = false, columnDefinition = "TEXT")
    private String filtersJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getReportKey() { return reportKey; }
    public void setReportKey(String reportKey) { this.reportKey = reportKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFiltersJson() { return filtersJson; }
    public void setFiltersJson(String filtersJson) { this.filtersJson = filtersJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
