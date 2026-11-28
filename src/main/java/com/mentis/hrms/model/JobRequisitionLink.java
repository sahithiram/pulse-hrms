package com.mentis.hrms.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_requisition_link")
public class JobRequisitionLink {
    @Id
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "requisition_id", nullable = false)
    private Long requisitionId;

    @Column(name = "linked_at", nullable = false)
    private LocalDateTime linkedAt = LocalDateTime.now();

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public Long getRequisitionId() { return requisitionId; }
    public void setRequisitionId(Long requisitionId) { this.requisitionId = requisitionId; }
    public LocalDateTime getLinkedAt() { return linkedAt; }
}
