package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_task_checklist", indexes = {
        @Index(name = "idx_pm_chk_task", columnList = "task_id"),
        @Index(name = "idx_pm_chk_parent", columnList = "parent_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaskChecklistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Builder.Default
    @Column(name = "done", nullable = false)
    private Boolean done = false;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (done == null) done = false;
        if (sortOrder == null) sortOrder = 0;
    }
}