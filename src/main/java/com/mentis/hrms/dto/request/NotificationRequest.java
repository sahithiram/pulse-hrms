package com.mentis.hrms.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRequest {

    /** EmployeeId string, e.g. "SA001" — matches Notification.recipientId */
    private String recipientId;
    /** EMPLOYEE | HR | PM — matches Notification.recipientType */
    private String recipientType;

    /** TASK_ASSIGNED | TASK_MENTIONED | CARD_MOVED | COMMENT_ADDED | DUE_SOON | OVERDUE | ... */
    private String type;
    /** Short action key used by inbox filters, mirrors type */
    private String action;

    private String title;
    private String message;

    /** Task/Project FK context — nullable */
    private Long taskId;
    private Long projectId;
    private Long actorId;

    /** Who triggered it — display name, e.g. "Priya Sharma" */
    private String actorName;

    /** LOW | NORMAL | HIGH */
    @Builder.Default
    private String priority = "NORMAL";

    /** Deep link for the toast click, e.g. "/pm/06-tasks?open=42" */
    private String deepLink;

    /** Persist to DB (true) or emit toast-only (false) */
    @Builder.Default
    private boolean persistent = true;
}
