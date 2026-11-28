package com.mentis.hrms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** RegularizationApprovedEvent (Sprint 5) → notification to the employee. */
@Component
public class RegularizationNotificationListener {

    @Autowired
    private HrmsNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRegularizationApproved(RegularizationApprovedEvent e) {
        notificationService.notify(e.employeeId, "REGULARIZATION_DECIDED",
                Map.of("id", String.valueOf(e.regularizationId),
                       "date", String.valueOf(e.date),
                       "status", "APPROVED"),
                "/attendance/regularize", "REG-" + e.regularizationId);
    }
}
