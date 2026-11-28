package com.mentis.hrms.service;

import com.mentis.hrms.model.AnnouncementPolicy;
import com.mentis.hrms.model.AnnouncementTarget;
import com.mentis.hrms.repository.AnnouncementPolicyRepository;
import com.mentis.hrms.repository.AnnouncementTargetRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REQ-6-06 scheduling + targeting + ack-required policy, WITHOUT touching the legacy announcements schema.
 * Visibility rule (app-side): an announcement is visible only if policy.publish_at is null or already reached.
 * The legacy read path must opt in — see 07_EVENT_WIRING_MIGRATION.md / README (we do not rewrite the legacy read path blindly).
 */
@Service
public class AnnouncementPolicyService {

    @Autowired private AnnouncementPolicyRepository policyRepository;
    @Autowired private AnnouncementTargetRepository targetRepository;

    @Transactional
    public AnnouncementPolicy setPolicy(Long announcementId, boolean requiresAck, LocalDateTime publishAt) {
        AnnouncementPolicy p = policyRepository.findById(announcementId).orElseGet(() -> {
            AnnouncementPolicy np = new AnnouncementPolicy();
            np.setAnnouncementId(announcementId);
            return np;
        });
        p.setRequiresAck(requiresAck);
        p.setPublishAt(publishAt);
        return policyRepository.save(p);
    }

    @Transactional
    public void setTargets(Long announcementId, String targetType, List<String> values) {
        targetRepository.deleteByAnnouncementId(announcementId);
        if (values == null || values.isEmpty()) return; // no rows = legacy ALL semantics
        for (String v : values) {
            if (v == null || v.isBlank()) continue;
            AnnouncementTarget t = new AnnouncementTarget();
            t.setAnnouncementId(announcementId);
            t.setTargetType(targetType);
            t.setTargetValue(v.trim());
            targetRepository.save(t);
        }
    }

    public Optional<AnnouncementPolicy> policy(Long announcementId) {
        return policyRepository.findById(announcementId);
    }

    public boolean requiresAck(Long announcementId) {
        return policyRepository.findById(announcementId).map(AnnouncementPolicy::isRequiresAck).orElse(false);
    }

    /** Visibility check for app-side filtering; publishAt null = publish immediately (legacy behavior). */
    public boolean isPublished(Long announcementId, LocalDateTime now) {
        return policyRepository.findById(announcementId)
                .map(p -> p.getPublishAt() == null || !p.getPublishAt().isAfter(now))
                .orElse(true);
    }

    public List<AnnouncementTarget> targets(Long announcementId) {
        return targetRepository.findByAnnouncementId(announcementId);
    }
}
