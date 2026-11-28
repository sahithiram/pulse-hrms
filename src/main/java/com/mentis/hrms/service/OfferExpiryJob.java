package com.mentis.hrms.service;

import com.mentis.hrms.model.OfferWorkflow;
import com.mentis.hrms.repository.OfferWorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REQ-7-04 — expiry sweep: SENT/APPROVED offers past valid_until → EXPIRED + HR notified.
 * Idempotent: only touches rows still in SENT/APPROVED. Requires @EnableScheduling (see README).
 */
@Service
public class OfferExpiryJob {

    private static final Logger logger = LoggerFactory.getLogger(OfferExpiryJob.class);

    @Autowired private OfferWorkflowRepository workflowRepository;
    @Autowired private HrmsNotificationService notificationService;

    @Value("${hrms.offer-expiry.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${hrms.offer-expiry.cron:0 15 6 * * *}")
    @Transactional
    public void run() {
        if (!enabled) return;
        LocalDate today = LocalDate.now();
        int expired = 0;
        for (String status : List.of(OfferWorkflow.SENT, OfferWorkflow.APPROVED)) {
            for (OfferWorkflow w : workflowRepository.findByStatusAndValidUntilBefore(status, today)) {
                w.setStatus(OfferWorkflow.EXPIRED);
                workflowRepository.save(w);
                notificationService.notify("HR", "GENERIC", Map.of(
                                "title", "Offer expired",
                                "body", "Offer #" + w.getOfferLetterId() + " expired on " + w.getValidUntil()),
                        "/offers/" + w.getOfferLetterId(), "OFFER-EXP-" + w.getOfferLetterId() + "-" + today);
                expired++;
            }
        }
        if (expired > 0) logger.info("OfferExpiryJob expired {} offer(s)", expired);
    }
}
