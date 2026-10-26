package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.scheduler.BlockedTaskEscalationScheduler;
import com.mentis.hrms.pm.scheduler.TaskDueReminderScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * DEV ONLY. Does not exist unless pm.dev-triggers.enabled=true.
 * Leave it false in production: any logged-in user could call it.
 *
 *   POST /api/pm/dev/escalation/run?hoursAhead=72      pretend it is 72h later
 *   POST /api/pm/dev/reminders/run?date=2026-10-30     run the daily reminders for that date
 */
@RestController
@RequestMapping("/api/pm/dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pm.dev-triggers.enabled", havingValue = "true")
public class PmDevTriggerController {

    private final BlockedTaskEscalationScheduler escalation;
    private final TaskDueReminderScheduler reminders;

    @PostMapping("/escalation/run")
    public Map<String, Object> runEscalation(@RequestParam(defaultValue = "0") long hoursAhead) {
        int sent = escalation.runAt(LocalDateTime.now().plusHours(hoursAhead));
        return Map.of("sent", sent, "hoursAhead", hoursAhead);
    }

    @PostMapping("/reminders/run")
    public Map<String, Object> runReminders(@RequestParam(required = false) String date) {
        LocalDate d = date == null ? LocalDate.now() : LocalDate.parse(date);
        int sent = reminders.runFor(d);
        return Map.of("sent", sent, "date", d.toString());
    }
}