package com.mentis.hrms.pm.entity;

import com.mentis.hrms.pm.enums.RetroCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_sprint_retro_item", indexes = {
        @Index(name = "idx_pm_retro_item_retro", columnList = "retro_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SprintRetroItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "retro_id", nullable = false)
    private Long retroId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private RetroCategory category;

    @Column(name = "item_text", nullable = false, length = 1000)
    private String text;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** set once the action item has been turned into a task; the task is never deleted with the item */
    @Column(name = "converted_task_id")
    private Long convertedTaskId;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (sortOrder == null) sortOrder = 0;
    }
}