package com.mentis.hrms.service;

import com.mentis.hrms.model.AnnouncementAck;
import com.mentis.hrms.repository.AnnouncementAckRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** REQ-6-06 — acknowledgement tracking for policy announcements. */
@Service
public class AnnouncementAckService {

    @Autowired
    private AnnouncementAckRepository ackRepository;

    @Transactional
    public AnnouncementAck ack(Long announcementId, String userId) {
        Optional<AnnouncementAck> existing = ackRepository.findByAnnouncementIdAndUserId(announcementId, userId);
        if (existing.isPresent()) return existing.get();
        AnnouncementAck ack = new AnnouncementAck();
        ack.setAnnouncementId(announcementId);
        ack.setUserId(userId);
        ack.setAckedAt(java.time.LocalDateTime.now());
        return ackRepository.save(ack);
    }

    public List<AnnouncementAck> acknowledgers(Long announcementId) {
        return ackRepository.findByAnnouncementId(announcementId);
    }

    public long ackCount(Long announcementId) {
        return ackRepository.countByAnnouncementId(announcementId);
    }

    /** HR view: list employees who have NOT acked (caller supplies the audience list). */
    public List<String> pendingAckers(Long announcementId, List<String> audienceUserIds) {
        List<String> done = ackRepository.findByAnnouncementId(announcementId).stream()
                .map(AnnouncementAck::getUserId).toList();
        return audienceUserIds.stream().filter(u -> !done.contains(u)).toList();
    }
}
