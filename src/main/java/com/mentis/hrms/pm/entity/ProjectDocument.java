package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_project_documents", indexes = @Index(name = "idx_pm_doc_project", columnList = "project_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** SOW, SPEC, CONTRACT, DESIGN, REPORT, LINK, OTHER */
    @Column(name = "category", nullable = false, length = 20)
    private String category;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** set for links, null for uploaded files */
    @Column(name = "link_url", length = 1000)
    private String linkUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;
}