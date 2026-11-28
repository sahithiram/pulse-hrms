package com.mentis.hrms.repository;

import com.mentis.hrms.model.HrmsNotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface HrmsNotificationPreferenceRepository extends JpaRepository<HrmsNotificationPreference, Long> {
    Optional<HrmsNotificationPreference> findByUserIdAndEventType(String userId, String eventType);
    List<HrmsNotificationPreference> findByUserId(String userId);
}
