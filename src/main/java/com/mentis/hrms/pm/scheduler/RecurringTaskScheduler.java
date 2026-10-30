package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.pm.service.RecurringTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTaskScheduler {

    private final RecurringTaskService service;

    @Scheduled(cron = "${pm.recurring.cron:0 5 0 * * *}", zone = "Asia/Kolkata")
    public void daily() {
        int n = service.runAll();
        log.info("Recurring tasks run finished: created={}", n);
    }
}