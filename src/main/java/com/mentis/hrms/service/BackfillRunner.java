package com.mentis.hrms.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Dev-only entry point. Will NOT run unless BOTH the dev profile is active AND\n * hrms.backfill.execute=true. After a successful staging run, leave it false.\n */
@Component
@Profile("dev")
public class BackfillRunner {

    private static final Logger logger = LoggerFactory.getLogger(BackfillRunner.class);

    @Value("${hrms.backfill.execute:false}")
    private boolean execute;

    private final BackfillService backfillService;

    public BackfillRunner(BackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @PostConstruct
    public void maybeRun() {
        if (!execute) {
            logger.info("BackfillRunner idle (set hrms.backfill.execute=true to run).");
            return;
        }
        logger.warn("BackfillRunner: executing full backfill — intended for RESTORED BACKUP only.");
        logger.info("Backfill report: {}", backfillService.runFullBackfill());
    }
}
