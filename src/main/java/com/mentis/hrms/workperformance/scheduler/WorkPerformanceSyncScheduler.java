package com.mentis.hrms.workperformance.scheduler;

import com.mentis.hrms.workperformance.service.PerformanceSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class WorkPerformanceSyncScheduler {

    private static final Logger logger = LoggerFactory.getLogger(WorkPerformanceSyncScheduler.class);

    @Autowired
    private PerformanceSyncService performanceSyncService;

    @Scheduled(cron = "0 0 2 1 * ?") // 2:00 AM on the 1st of each month
    public void syncLastMonthForAllEmployees() {
        try {
            LocalDate lastMonth = LocalDate.now().minusMonths(1);
            logger.info("Starting monthly ClickUp work-performance sync for {}-{}",
                    lastMonth.getYear(), lastMonth.getMonthValue());

            var results = performanceSyncService.syncAll(lastMonth.getYear(), lastMonth.getMonthValue());

            logger.info("Monthly work-performance sync complete. {} employees processed.", results.size());
        } catch (Exception e) {
            logger.error("Monthly work-performance sync failed: {}", e.getMessage());
        }
    }
}
