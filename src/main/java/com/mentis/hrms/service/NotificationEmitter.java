package com.mentis.hrms.service;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.model.Notification;
import com.mentis.hrms.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 4 — unified emitter for HRMS + PM notifications.
 * Persists one row, broadcasts to:
 *   /topic/user/{employeeId}/notifications       (personal bell)
 *   /topic/project/{projectId}/events            (per-project board updates)
 *   /topic/global/events                         (announcements)
 *   /topic/pm/events                             (back-compat for pm-core.js)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEmitter {

    private final NotificationRepository repo;
    private final SimpMessagingTemplate messaging;

    @Transactional
    public Notification emit(NotificationRequest req) {
        Notification n = new Notification();
        n.setRecipientId(req.getRecipientId());
        n.setRecipientType(req.getRecipientType());
        n.setType(req.getType());
        n.setTitle(req.getTitle());
        n.setMessage(req.getMessage());
        n.setSender(req.getActorName());              // existing field
        n.setReferenceId(req.getTaskId() == null ? null : String.valueOf(req.getTaskId()));
        n.setReferenceType(req.getTaskId() == null ? null : "TASK");
        n.setTaskId(req.getTaskId());
        n.setProjectId(req.getProjectId());
        n.setActorId(req.getActorId());
        n.setAction(req.getAction() != null ? req.getAction() : req.getType());
        n.setPriority(req.getPriority());
        n.setDeepLink(req.getDeepLink());
        n.setCreatedAt(LocalDateTime.now());
        n.setReadAt(null);
        n.setPersistent(req.isPersistent());

        Notification saved = repo.save(n);

        broadcast(saved);
        return saved;
    }

    private void broadcast(Notification n) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", n.getId());
        payload.put("type", n.getType());
        payload.put("action", n.getAction());
        payload.put("title", n.getTitle());
        payload.put("message", n.getMessage());
        payload.put("taskId", n.getTaskId());
        payload.put("projectId", n.getProjectId());
        payload.put("actorId", n.getActorId());
        payload.put("sender", n.getSender());
        payload.put("priority", n.getPriority());
        payload.put("deepLink", n.getDeepLink());
        payload.put("referenceId", n.getReferenceId());
        payload.put("referenceType", n.getReferenceType());
        payload.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
        payload.put("persistent", n.isPersistent());

        // 1) personal bell — keyed by recipientId string (e.g. "SA001")
        try {
            messaging.convertAndSend(
                    "/topic/user/" + n.getRecipientId() + "/notifications", payload);
        } catch (Exception e) {
            log.warn("Per-user broadcast failed for {}: {}", n.getRecipientId(), e.getMessage());
        }

        // 2) per-project board — keyed by projectId
        if (n.getProjectId() != null) {
            try {
                messaging.convertAndSend(
                        "/topic/project/" + n.getProjectId() + "/events", payload);
            } catch (Exception e) {
                log.warn("Per-project broadcast failed for {}: {}", n.getProjectId(), e.getMessage());
            }
        }

        // 3) legacy PM topic (keeps pm-core.js working with zero changes)
        try {
            messaging.convertAndSend("/topic/pm/events", payload);
        } catch (Exception e) {
            log.warn("Legacy PM broadcast failed: {}", e.getMessage());
        }
    }
}
