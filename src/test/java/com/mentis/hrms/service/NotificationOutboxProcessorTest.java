package com.mentis.hrms.service;

import com.mentis.hrms.model.HrmsNotificationOutbox;
import com.mentis.hrms.repository.HrmsNotificationOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** NOTE: not executed yet — mvn -Dtest=NotificationOutboxProcessorTest test */
class NotificationOutboxProcessorTest {

    private NotificationOutboxProcessor processor;
    private HrmsNotificationOutboxRepository repo;
    private JavaMailSender mailSender;

    @BeforeEach
    void setUp() throws Exception {
        processor = new NotificationOutboxProcessor();
        repo = Mockito.mock(HrmsNotificationOutboxRepository.class);
        mailSender = Mockito.mock(JavaMailSender.class);
        set("outboxRepository", repo);
        set("mailSender", mailSender);
        set("maxAttempts", 5);
        set("batchSize", 50);
        set("fromAddress", "no-reply@mentis.example");
        when(repo.save(any(HrmsNotificationOutbox.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private HrmsNotificationOutbox jobWithAttempts(int attempts) {
        HrmsNotificationOutbox j = new HrmsNotificationOutbox();
        j.setEventId("E-1:LEAVE_APPROVED:EMP1");
        j.setEventType("LEAVE_APPROVED");
        j.setUserId("emp@mentis.example");
        j.setSubject("Leave approved");
        j.setBody("Body");
        j.setAttempts(attempts);
        j.setStatus("PENDING");
        j.setNextRetryAt(LocalDateTime.now().minusMinutes(1));
        return j;
    }

    @Test
    void failure_incrementsAttempts_andSchedulesBackoff() {
        HrmsNotificationOutbox job = jobWithAttempts(0);
        when(repo.findDue(any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(job));
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        processor.processDue();

        assertEquals(1, job.getAttempts());
        assertEquals("PENDING", job.getStatus());
        assertNotNull(job.getNextRetryAt());
        assertTrue(job.getNextRetryAt().isAfter(LocalDateTime.now()), "retry must be in the future");
        assertTrue(job.getLastError().contains("SMTP down"), "lastError should capture cause");
    }

    @Test
    void failureAtMaxedAttempts_marksFAILED_noDuplicateOnRetry() {
        HrmsNotificationOutbox job = jobWithAttempts(4); // 5th failure -> FAILED
        when(repo.findDue(any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(job));
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        processor.processDue();

        assertEquals(5, job.getAttempts());
        assertEquals("FAILED", job.getStatus());
    }

    @Test
    void resendOnlyFromFAILED() {
        HrmsNotificationOutbox job = jobWithAttempts(5);
        job.setStatus("PENDING");
        when(repo.findById(99L)).thenReturn(java.util.Optional.of(job));
        assertThrows(IllegalStateException.class, () -> processor.resend(99L));

        job.setStatus("FAILED");
        assertTrue(processor.resend(99L));
        assertEquals("PENDING", job.getStatus());
        assertEquals(0, job.getAttempts());
    }

    @Test
    void missingMailSender_marksFAILED_withReason_noSilentLoss() throws Exception {
        set("mailSender", null);
        HrmsNotificationOutbox job = jobWithAttempts(0);
        when(repo.findDue(any(LocalDateTime.class), any(Pageable.class))).thenReturn(List.of(job));

        processor.processDue();

        assertEquals("FAILED", job.getStatus());
        assertTrue(job.getLastError().contains("JavaMailSender"));
    }

    private void set(String field, Object value) throws Exception {
        var f = NotificationOutboxProcessor.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(processor, value);
    }
}
