package com.mentis.hrms.service;

import com.mentis.hrms.model.AuditLog;
import com.mentis.hrms.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Append-only audit writer. NEVER throws — audit failure must not break business flow.\n * Note: writes are synchronous by default; switch to @Async with a dedicated executor\n * when volume demands it.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String action, Long actorId, String actorRole,
                      String entityType, String entityId,
                      String oldValueJson, String newValueJson,
                      HttpServletRequest request, String detailsJson) {
        try {
            AuditLog row = new AuditLog();
            row.setAction(action);
            row.setActorId(actorId);
            row.setActorRole(actorRole);
            row.setEntityType(entityType);
            row.setEntityId(entityId);
            row.setOldValue(oldValueJson);
            row.setNewValue(newValueJson);
            row.setIpAddress(clientIp(request));
            row.setUserAgent(truncate(userAgent(request), 500));
            row.setTraceId(traceId());
            row.setDetails(detailsJson);
            auditLogRepository.save(row);
        } catch (Exception e) {
            // Deliberately swallow: audit must never take down a login or a leave approval.
            logger.error("AUDIT WRITE FAILED action={} actor={} entity={}:{} — {}",
                    action, actorId, entityType, entityId, e.toString());
        }
    }

    public void auditHttpMutation(String method, String path, Long actorId, String actorRole,
                                  HttpServletRequest request, String detailsJson) {
        audit("HTTP_" + method, actorId, actorRole, "http_path", path, null, null, request, detailsJson);
    }

    private static String traceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static String userAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }

    private static String truncate(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
