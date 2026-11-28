package com.mentis.hrms.repository;

import com.mentis.hrms.model.HrmsNotificationOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface HrmsNotificationOutboxRepository extends JpaRepository<HrmsNotificationOutbox, Long> {

    @Query("SELECT o FROM HrmsNotificationOutbox o WHERE o.status = 'PENDING' AND o.nextRetryAt <= :now ORDER BY o.nextRetryAt ASC")
    List<HrmsNotificationOutbox> findDue(@Param("now") LocalDateTime now, org.springframework.data.domain.Pageable pageable);

    boolean existsByEventId(String eventId);
    List<HrmsNotificationOutbox> findByStatusOrderByIdDesc(String status, org.springframework.data.domain.Pageable pageable);
}
