package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.pm.service.ProjectFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 06:00 daily: refresh cost + automatic health of every project (notify on a worse result).
 * Monday 08:00: send the weekly status update to each active project's owner and managers.
 * Never touches PmAccessService (no web user here).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHealthScheduler {

    private final ProjectFinanceService finance;

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Kolkata")
    public void nightly() {
        runNightly();
    }

    public int runNightly() {
        int changed = 0, failed = 0;
        for (Long id : finance.allProjectIds()) {
            try {
                if (finance.recalculateOne(id)) changed++;
            } catch (Exception e) {
                failed++;
                log.warn("health recalculation failed for project {}: {}", id, e.getMessage());
            }
        }
        log.info("ProjectHealthScheduler nightly: {} health change(s), {} failure(s)", changed, failed);
        return changed;
    }

    @Scheduled(cron = "0 0 8 * * MON", zone = "Asia/Kolkata")
    public void weekly() {
        runWeekly();
    }

    public int runWeekly() {
        int sent = 0;
        for (Long id : finance.allProjectIds()) {
            try {
                sent += finance.sendStatusReport(id, false);
            } catch (Exception e) {
                log.warn("status update failed for project {}: {}", id, e.getMessage());
            }
        }
        log.info("ProjectHealthScheduler weekly: {} notification(s)", sent);
        return sent;
    }
}