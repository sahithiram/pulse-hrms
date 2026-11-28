package com.mentis.hrms.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentis.hrms.repository.HrmsNotificationRepository;
import java.time.LocalDateTime;

/** REQ-6-03 — retention: delete READ in-app notifications older than hrms.retention.days (default 30). */
@Service
public class NotificationRetentionCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(NotificationRetentionCleanupJob.class);

    @Autowired
    private HrmsNotificationRepository notificationRepository;

    @Value("${hrms.retention.days:30}")
    private int retentionDays;

    @Scheduled(cron = "${hrms.retention.cron:0 30 2 * * *}")
    @Transactional
    public void run() {
        int deleted = notificationRepository.deleteReadOlderThan(LocalDateTime.now().minusDays(retentionDays));
        logger.info("NotificationRetentionCleanupJob: deleted {} read notification(s) older than {} days", deleted, retentionDays);
    }
}
