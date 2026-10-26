package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.dto.NotificationRequest;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.service.TaskEventNotifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Step 5 — escalates tasks that stay BLOCKED.
 *   level 1 (default 2 days): project owner, MANAGER/LEAD members, team lead
 *   level 2 (default 5 days): project owner, MANAGERs, and the Super Admin codes
 *                             from pm.escalation.super-admin-codes
 * Each level fires ONCE per blocked period (escalation_level is stored on the task
 * and reset to 0 when the task leaves BLOCKED).
 *
 * Runs with no logged-in user, so it NEVER calls PmAccessService.
 * Switch off with pm.escalation.enabled=false.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "pm.escalation.enabled", havingValue = "true", matchIfMissing = true)
public class BlockedTaskEscalationScheduler {

    private final TaskRepository taskRepository;
    private final TaskEventNotifier notifier;

    @Value("${pm.escalation.level1-days:2}")
    private int level1Days;

    @Value("${pm.escalation.level2-days:5}")
    private int level2Days;

    @Value("${pm.escalation.super-admin-codes:}")
    private String superAdminCodes;

    @Scheduled(cron = "${pm.escalation.cron:0 15 * * * *}")
    public void run() {
        runAt(LocalDateTime.now());
    }

    /** Public so a dev trigger or test can pass a pretend "now". Returns bells sent. */
    public int runAt(LocalDateTime now) {
        int sent = 0;
        for (Task t : taskRepository.findByStatus(TaskStatus.BLOCKED)) {
            try {
                // blocked_since if known, else last update (the SQL backfill sets it for old rows)
                LocalDateTime since = t.getBlockedSince() != null ? t.getBlockedSince()
                        : (t.getUpdatedAt() != null ? t.getUpdatedAt() : now);
                long days = Duration.between(since, now).toDays();
                int level = days >= level2Days ? 2 : (days >= level1Days ? 1 : 0);
                int done = t.getEscalationLevel() == null ? 0 : t.getEscalationLevel();
                if (level == 0 || level <= done) continue;

                sent += (level == 2) ? escalate(t, days) : warn(t, days);
                taskRepository.updateEscalationLevel(t.getId(), level);   // JPQL: does not touch updated_at
            } catch (Exception e) {
                log.warn("escalation failed for task {}: {}", t.getId(), e.getMessage());
            }
        }
        log.info("BlockedTaskEscalationScheduler sent {} notification(s)", sent);
        return sent;
    }

    private int warn(Task t, long days) {
        Set<Long> pks = notifier.leaders(t.getProjectId(), t.getTeamId());
        List<NotificationRequest> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Long pk : pks) {
            String code = notifier.codeOf(pk);
            if (code == null || !seen.add(code)) continue;
            out.add(notifier.build(code, "TASK_BLOCKED_LONG",
                    "Still blocked: " + t.getTitle(),
                    "Blocked for " + days + " day(s). Open the task to see the reason.",
                    "HIGH", t.getId(), t.getProjectId(),
                    "/pm/06-tasks?view=board&open=" + t.getId(), null, "System"));
        }
        return notifier.sendAll(out);
    }

    private int escalate(Task t, long days) {
        Set<String> codes = new LinkedHashSet<>();
        for (Long pk : notifier.ownerAndManagers(t.getProjectId())) {
            String c = notifier.codeOf(pk);
            if (c != null) codes.add(c);
        }
        if (superAdminCodes != null) {
            for (String c : superAdminCodes.split(",")) {
                if (!c.isBlank()) codes.add(c.trim());
            }
        }
        List<NotificationRequest> out = new ArrayList<>();
        for (String code : codes) {
            out.add(notifier.build(code, "TASK_ESCALATION",
                    "Escalation: blocked " + days + " days — " + t.getTitle(),
                    "This task has been blocked for " + days + " days and needs a decision.",
                    "HIGH", t.getId(), t.getProjectId(),
                    "/pm/06-tasks?view=board&open=" + t.getId(), null, "System"));
        }
        return notifier.sendAll(out);
    }
}