package com.mentis.hrms.service;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

/**
 * Thin writer over the Sprint-2 audit_log table — column list verified in V4__sprint2_security.sql:
 * occurred_at, actor_id, actor_role, action, entity_type, entity_id, old_value/new_value/details (JSON).
 * Append-only — this class never issues UPDATE/DELETE against audit_log.
 */
@Component
public class AuditWriter {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** details must be valid JSON (stored in a JSON column) — build it with {@link #json(String...)}. */
    public void write(String action, String entityType, String entityId, String detailsJson, String actorRole) {
        jdbcTemplate.update(
                "INSERT INTO audit_log (occurred_at, actor_role, action, entity_type, entity_id, details) VALUES (?,?,?,?,?,?)",
                LocalDateTime.now(), actorRole, action, entityType, entityId, detailsJson);
    }

    /** Escapes values so the result is always valid JSON object text. */
    public static String json(String... kvPairs) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(kvPairs[i])).append("\":\"").append(escape(kvPairs[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
