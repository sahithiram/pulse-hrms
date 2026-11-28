package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "application_source")
public class ApplicationSource {
    @Id
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "source", nullable = false, length = 24)
    private String source;

    @Column(name = "source_detail", length = 128)
    private String sourceDetail;

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSourceDetail() { return sourceDetail; }
    public void setSourceDetail(String sourceDetail) { this.sourceDetail = sourceDetail; }
}
