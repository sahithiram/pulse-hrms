package com.mentis.hrms.service;

import com.mentis.hrms.model.HrmsNotificationOutbox;
import com.mentis.hrms.repository.HrmsNotificationOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REQ-6-02 — async email delivery with exponential backoff. FAILED after `hrms.outbox.max-attempts` (default 5).
 * Requires @EnableScheduling somewhere in the app (add to main application class — see README).
 * Backoff: attempt n → 5 * 4^(n-1) minutes (5, 20, 80, 320…), capped at 24h.
 */
@Service
public class NotificationOutboxProcessor {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOutboxProcessor.class);

    @Autowired
    private HrmsNotificationOutboxRepository outboxRepository;

    /** Email sender is OPTIONAL — if absent, outbox rows are marked FAILED with a clear reason (no silent loss). */
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${hrms.outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${hrms.outbox.batch-size:50}")
    private int batchSize;

    @Value("${hrms.outbox.from:no-reply@mentis.example}")
    private String fromAddress;

    @Scheduled(fixedDelayString = "${hrms.outbox.poll-ms:60000}")
    @Transactional
    public void processDue() {
        List<HrmsNotificationOutbox> due = outboxRepository.findDue(
                LocalDateTime.now(), PageRequest.of(0, batchSize));
        for (HrmsNotificationOutbox job : due) {
            deliver(job);
        }
        if (!due.isEmpty()) {
            logger.info("Outbox processed {} row(s)", due.size());
        }
    }

    @Transactional
    public boolean resend(Long outboxId) {
        return outboxRepository.findById(outboxId).map(job -> {
            if (!"FAILED".equals(job.getStatus())) {
                throw new IllegalStateException("Only FAILED outbox rows can be resent");
            }
            job.setStatus("PENDING");
            job.setAttempts(0);
            job.setNextRetryAt(LocalDateTime.now());
            outboxRepository.save(job);
            return true;
        }).orElse(false);
    }

    private void deliver(HrmsNotificationOutbox job) {
        if (mailSender == null) {
            job.setStatus("FAILED");
            job.setLastError("JavaMailSender not configured (mail properties missing)");
            job.setAttempts(job.getAttempts() + 1);
            outboxRepository.save(job);
            logger.error("Outbox {} cannot send: JavaMailSender missing", job.getId());
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            String recipient = job.getRecipient() != null ? job.getRecipient() : resolveRecipient(job.getUserId());
            msg.setTo(recipient);
            msg.setSubject(job.getSubject());
            msg.setText(job.getBody() == null ? "" : job.getBody());
            mailSender.send(msg);
            job.setStatus("SENT");
            job.setSentAt(LocalDateTime.now());
            job.setLastError(null);
            outboxRepository.save(job);
        } catch (Exception e) {
            int attempts = job.getAttempts() + 1;
            job.setAttempts(attempts);
            job.setLastError(String.valueOf(e.getMessage()).substring(0, Math.min(500, String.valueOf(e.getMessage()).length())));
            if (attempts >= maxAttempts) {
                job.setStatus("FAILED");
                logger.error("Outbox {} FAILED after {} attempts: {}", job.getId(), attempts, e.getMessage());
            } else {
                long backoffMinutes = Math.min(24 * 60L, 5L * (long) Math.pow(4, attempts - 1));
                job.setNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                logger.warn("Outbox {} attempt {} failed, retry in {} min", job.getId(), attempts, backoffMinutes);
            }
            outboxRepository.save(job);
        }
    }

    /** Default recipient resolution = userId is an email or mapped to one upstream. Override with a real lookup when wired to employees. */
    private String resolveRecipient(String userId) {
        return userId != null && userId.contains("@") ? userId : userId + "@mentis.example";
    }
}
