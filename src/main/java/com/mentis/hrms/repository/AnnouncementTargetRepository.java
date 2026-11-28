package com.mentis.hrms.repository;

import com.mentis.hrms.model.AnnouncementTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnnouncementTargetRepository extends JpaRepository<AnnouncementTarget, Long> {
    List<AnnouncementTarget> findByAnnouncementId(Long announcementId);
    List<AnnouncementTarget> findByTargetTypeAndTargetValue(String targetType, String targetValue);
    void deleteByAnnouncementId(Long announcementId);
}
