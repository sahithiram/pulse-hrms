package com.mentis.hrms.service;

import com.mentis.hrms.model.HrmsNotification;
import com.mentis.hrms.model.HrmsNotificationOutbox;
import com.mentis.hrms.model.HrmsNotificationPreference;
import com.mentis.hrms.repository.HrmsNotificationOutboxRepository;
import com.mentis.hrms.repository.HrmsNotificationPreferenceRepository;
import com.mentis.hrms.repository.HrmsNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * REQ-6-01/02 — central templates + preferences + transactional outbox.
 * Business code calls {@link #notify(String, String, String, String, String)} INSIDE its transaction:
 * the in-app row and the EMAIL outbox row are written atomically with the business change.
 * The actual email send happens AFTER commit via {@link NotificationOutboxProcessor}.
 */
@Service
public class HrmsNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(HrmsNotificationService.class);

    /** event_type → template with {name}-style placeholders (missing keys render as empty, never throw). */
    private static final Map<String, String[]> TEMPLATES = Map.ofEntries(
            Map.entry("LEAVE_SUBMITTED", new String[]{"Leave request {code} submitted", "Your leave request {code} ({from} → {to}) has been submitted for approval."}),
            Map.entry("LEAVE_APPROVED", new String[]{"Leave request {code} approved", "Your leave request {code} ({from} → {to}) was approved by {approver}."}),
            Map.entry("LEAVE_REJECTED", new String[]{"Leave request {code} rejected", "Your leave request {code} was rejected. Reason: {reason}"}),
            Map.entry("LEAVE_CANCELLED", new String[]{"Leave request {code} cancelled", "Your leave request {code} cancellation was processed."}),
            Map.entry("REGULARIZATION_DECIDED", new String[]{"Regularization {id} {status}", "Your regularization request #{id} for {date} was {status}."}),
            Map.entry("DOCUMENT_VERIFIED", new String[]{"Document {doc} verified", "Your document {doc} has been verified."}),
            Map.entry("DOCUMENT_REJECTED", new String[]{"Document {doc} rejected", "Your document {doc} was rejected. Reason: {reason}"}),
            Map.entry("ONBOARDING_DEADLINE", new String[]{"Onboarding document deadline {when}", "Submit your pending onboarding documents by {when}."}),
            Map.entry("OFFER_SENT", new String[]{"Offer sent: {role}", "An offer for {role} was sent to {candidate}."}),
            Map.entry("OFFER_ACCEPTED", new String[]{"Offer accepted", "{candidate} accepted the offer for {role}."}),
            Map.entry("OFFER_EXPIRED", new String[]{"Offer expired", "The offer for {role} to {candidate} has expired."}),
            Map.entry("PROBATION_REVIEW_DUE", new String[]{"Probation review due", "Probation review for {employee} is due on {date}."}),
            Map.entry("MANAGER_REASSIGNMENT_NEEDED", new String[]{"Manager reassignment needed", "Direct reports of {employee} need a new manager after exit."}),
            Map.entry("MISSING_CHECKOUT_DIGEST", new String[]{"Missing check-out", "You did not check out on {date}. Please regularize or contact HR."}),
            Map.entry("GENERIC", new String[]{"{title}", "{body}"})
    );

    @Autowired private HrmsNotificationRepository notificationRepository;
    @Autowired private HrmsNotificationPreferenceRepository preferenceRepository;
    @Autowired private HrmsNotificationOutboxRepository outboxRepository;

    @Transactional
    public HrmsNotification notify(String userId, String eventType, Map<String, String> placeholders,
                                   String deepLink, String eventId) {
        return doNotify(userId, eventType, placeholders, deepLink, eventId, null);
    }

    /** Same as notify() but sets an explicit email recipient (falls back to userId mapping when null). */
    @Transactional
    public HrmsNotification notifyWithRecipient(String userId, String eventType, Map<String, String> placeholders,
                                                String deepLink, String eventId, String recipient) {
        return doNotify(userId, eventType, placeholders, deepLink, eventId, recipient);
    }

    private HrmsNotification doNotify(String userId, String eventType, Map<String, String> placeholders,
                                      String deepLink, String eventId, String recipient) {
        Map<String, String> ph = placeholders == null ? Map.of() : placeholders;
        String[] tpl = TEMPLATES.getOrDefault(eventType == null ? "GENERIC" : eventType, TEMPLATES.get("GENERIC"));
        String title = render(tpl[0], ph);
        String body = render(tpl[1], ph);

        HrmsNotificationPreference pref = preferenceRepository
                .findByUserIdAndEventType(userId, eventType).orElse(null);
        boolean wantsInApp = pref == null || pref.isInApp();
        boolean wantsEmail = pref == null || pref.isEmail();

        HrmsNotification inApp = null;
        if (wantsInApp) {
            inApp = new HrmsNotification();
            inApp.setUserId(userId);
            inApp.setEventType(eventType);
            inApp.setTitle(title);
            inApp.setBody(body);
            inApp.setDeepLink(deepLink);
            inApp.setChannel("IN_APP");
            inApp.setStatus("UNREAD");
            notificationRepository.save(inApp);
        }

        if (wantsEmail && eventId != null) {
            if (!outboxRepository.existsByEventId(eventId + ":" + eventType + ":" + userId)) {
                HrmsNotificationOutbox job = new HrmsNotificationOutbox();
                job.setEventId(eventId + ":" + eventType + ":" + userId);
                job.setEventType(eventType);
                job.setUserId(userId);
                job.setChannel("EMAIL");
                job.setSubject(title);
                job.setBody(body);
                if (recipient != null) { job.setRecipient(recipient); }
                job.setStatus("PENDING");
                outboxRepository.save(job);
            }
        }
        return inApp;
    }

    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndStatus(userId, "UNREAD");
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllRead(userId, java.time.LocalDateTime.now());
    }

    @Transactional
    public boolean markRead(Long notificationId, String userId) {
        return notificationRepository.findById(notificationId)
                .filter(n -> n.getUserId().equals(userId))
                .map(n -> {
                    n.setStatus("READ");
                    n.setReadAt(java.time.LocalDateTime.now());
                    notificationRepository.save(n);
                    return true;
                }).orElse(false);
    }

    private static String render(String template, Map<String, String> ph) {
        if (template == null) return "";
        String out = template;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        // strip any unresolved placeholders rather than leaking {braces}
        return out.replaceAll("\\{[A-Za-z0-9_]+\\}", "");
    }
}
