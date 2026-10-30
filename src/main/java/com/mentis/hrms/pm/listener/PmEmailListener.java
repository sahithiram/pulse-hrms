package com.mentis.hrms.pm.listener;

import com.mentis.hrms.event.PmEmailEvent;
import com.mentis.hrms.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class PmEmailListener {

    private final EmailService emailService;

    @Value("${pm.notify.email.enabled:true}")
    private boolean enabled;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(PmEmailEvent e) {
        if (!enabled) return;
        try {
            emailService.send(e.to(), e.subject(), e.body());
        } catch (Exception ex) {
            log.warn("PM email to {} failed: {}", e.to(), ex.getMessage());
        }
    }
}