package com.mentis.hrms.service;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding-window login throttle (in-memory; single node).\n * Rule: maxAttempts within windowSeconds per (ip|email) pair → locked until oldest attempt expires.\n * For multi-node deployments replace the map with Redis/Caffeine — see manifest §G.\n */
@Service
public class LoginThrottleService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = 15L * 60L * 1000L; // 15 minutes

    private final Map<String, ConcurrentLinkedDeque<Long>> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String key) {
        prune(key);
        ConcurrentLinkedDeque<Long> q = attempts.get(key);
        return q != null && q.size() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String key) {
        prune(key);
        attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>()).addLast(Instant.now().toEpochMilli());
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    public long retryAfterSeconds(String key) {
        ConcurrentLinkedDeque<Long> q = attempts.get(key);
        if (q == null || q.isEmpty()) return 0;
        long oldest = q.peekFirst();
        long unlockAt = oldest + WINDOW_MS;
        return Math.max(0, (unlockAt - Instant.now().toEpochMilli()) / 1000);
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictStaleEntries() {
        long cutoff = Instant.now().toEpochMilli() - WINDOW_MS;
        attempts.entrySet().removeIf(en -> {
            prune(en.getKey());
            return en.getValue().isEmpty();
        });
    }

    private void prune(String key) {
        ConcurrentLinkedDeque<Long> q = attempts.get(key);
        if (q == null) return;
        long cutoff = Instant.now().toEpochMilli() - WINDOW_MS;
        while (true) {
            Long head = q.peekFirst();
            if (head == null || head >= cutoff) break;
            q.pollFirst();
        }
        if (q.isEmpty()) attempts.remove(key, q);
    }
}
