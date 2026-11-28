package com.mentis.hrms.repository;

import com.mentis.hrms.model.HrmsNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface HrmsNotificationRepository extends JpaRepository<HrmsNotification, Long> {
    Page<HrmsNotification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    long countByUserIdAndStatus(String userId, String status);

    @Modifying
    @Query("UPDATE HrmsNotification n SET n.status='READ', n.readAt=:now WHERE n.userId=:userId AND n.status='UNREAD'")
    int markAllRead(@Param("userId") String userId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("DELETE FROM HrmsNotification n WHERE n.readAt < :cutoff")
    int deleteReadOlderThan(@Param("cutoff") LocalDateTime cutoff);

    List<HrmsNotification> findByUserIdAndStatusOrderByCreatedAtDesc(String userId, String status, Pageable pageable);
}
