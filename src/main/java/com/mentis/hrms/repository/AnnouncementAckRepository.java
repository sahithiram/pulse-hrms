package com.mentis.hrms.repository;

import com.mentis.hrms.model.AnnouncementAck;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AnnouncementAckRepository extends JpaRepository<AnnouncementAck, Long> {
    Optional<AnnouncementAck> findByAnnouncementIdAndUserId(Long announcementId, String userId);
    List<AnnouncementAck> findByAnnouncementId(Long announcementId);
    long countByAnnouncementId(Long announcementId);
}
