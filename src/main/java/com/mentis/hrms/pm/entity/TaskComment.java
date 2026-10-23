package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import lombok.Builder.Default;  // (or just use @Builder.Default - no separate import needed)
@Entity
@Table(name = "pm_task_comments", indexes = {
        @Index(name = "idx_pm_cmt_task", columnList = "task_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "mentions", length = 500)
    private String mentions;   // comma-separated employee ids

    @Builder.Default
    @Column(name = "edited", nullable = false)
    private Boolean edited = false;

    @Builder.Default
    @Column(name = "deleted", nullable = false)
    private Boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}