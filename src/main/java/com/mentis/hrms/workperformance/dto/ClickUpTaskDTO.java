package com.mentis.hrms.workperformance.dto;

/**
 * Plain data holder for one ClickUp task, mapped from the ClickUp API JSON response.
 * This is a NEW file — does not touch any existing DTO.
 */
public class ClickUpTaskDTO {

    private String id;
    private String name;
    private String status;       // e.g. "complete", "in progress", "to do"
    private String statusType;   // ClickUp's own category: "open" | "custom" | "done" | "closed"
    private Long dueDate;        // epoch millis, nullable
    private Long dateClosed;     // epoch millis, nullable
    private Long timeEstimate;   // milliseconds, nullable
    private Long timeSpent;      // milliseconds, nullable (from time tracking, if used)
    private String priority;     // "urgent" | "high" | "normal" | "low" | null
    private String assigneeId;

    // Status-name keywords that mean "NOT actually completed," even when
    // ClickUp buckets them under type "closed" or "done" — e.g. "rejected"
    // or "on hold" are type "done" in this workspace but aren't real completions.
    private static final String[] NEGATIVE_STATUS_KEYWORDS = {
            "reject", "drop", "miss", "hold", "reserved", "cancel", "block", "inactive"
    };

    /**
     * A task counts as finished when ClickUp's status type is "closed" OR
     * "done" — matching how ClickUp's own Brain assistant counts completion
     * ("14 closed and 2 marked done") — UNLESS the status text itself signals
     * a non-completion (rejected, on hold, dropped, reserved, etc.), which
     * several "done"-type statuses in this workspace represent.
     */
    public boolean isCompleted() {
        if (statusType == null) return false;
        boolean bucketedAsDone = "closed".equalsIgnoreCase(statusType) || "done".equalsIgnoreCase(statusType);
        if (!bucketedAsDone) return false;

        String lowerStatus = status != null ? status.toLowerCase() : "";
        for (String keyword : NEGATIVE_STATUS_KEYWORDS) {
            if (lowerStatus.contains(keyword)) return false;
        }
        return true;
    }

    public boolean isCompletedOnTime() {
        return isCompleted() && dueDate != null && dateClosed != null && dateClosed <= dueDate;
    }

    // ===== Getters & Setters =====
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusType() { return statusType; }
    public void setStatusType(String statusType) { this.statusType = statusType; }

    public Long getDueDate() { return dueDate; }
    public void setDueDate(Long dueDate) { this.dueDate = dueDate; }

    public Long getDateClosed() { return dateClosed; }
    public void setDateClosed(Long dateClosed) { this.dateClosed = dateClosed; }

    public Long getTimeEstimate() { return timeEstimate; }
    public void setTimeEstimate(Long timeEstimate) { this.timeEstimate = timeEstimate; }

    public Long getTimeSpent() { return timeSpent; }
    public void setTimeSpent(Long timeSpent) { this.timeSpent = timeSpent; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
}
