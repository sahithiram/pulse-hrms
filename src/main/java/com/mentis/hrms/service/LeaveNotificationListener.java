package com.mentis.hrms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Sprint 4 leave events are declared "published AFTER the approving transaction commits".
 * Listener therefore runs AFTER_COMMIT with fallbackExecution=true (safe even if a publisher
 * emits post-commit manually) and opens its OWN transaction to write in-app + outbox rows.
 * Residual risk window (post-commit, pre-enqueue crash) is documented in README; the outbox row
 * is written atomically with the in-app row inside this listener.
 */
@Component
public class LeaveNotificationListener {

    @Autowired
    private HrmsNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLeaveApproved(LeaveApprovedEvent e) {
        notificationService.notify(e.employeeId, "LEAVE_APPROVED",
                Map.of("code", String.valueOf(e.requestId),
                       "from", String.valueOf(e.startDate),
                       "to", String.valueOf(e.endDate),
                       "approver", String.valueOf(e.approverId)),
                "/leave/my", String.valueOf(e.requestId));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLeaveCancelled(LeaveCancelledEvent e) {
        notificationService.notify(e.employeeId, "LEAVE_CANCELLED",
                Map.of("code", String.valueOf(e.requestId),
                       "from", e.releasedDates == null || e.releasedDates.isEmpty() ? "" : String.valueOf(e.releasedDates.get(0)),
                       "to", e.releasedDates == null || e.releasedDates.isEmpty() ? "" : String.valueOf(e.releasedDates.get(e.releasedDates.size() - 1))),
                "/leave/my", String.valueOf(e.requestId) + ":CANCEL");
    }
}
