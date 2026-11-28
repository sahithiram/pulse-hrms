package com.mentis.hrms.service;

import com.mentis.hrms.model.HrmsNotification;
import com.mentis.hrms.repository.HrmsNotificationOutboxRepository;
import com.mentis.hrms.repository.HrmsNotificationPreferenceRepository;
import com.mentis.hrms.repository.HrmsNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** NOTE: not executed yet — mvn -Dtest=HrmsNotificationServiceTest test */
class HrmsNotificationServiceTest {

    private HrmsNotificationService service;
    private HrmsNotificationRepository notificationRepository;
    private HrmsNotificationPreferenceRepository preferenceRepository;
    private HrmsNotificationOutboxRepository outboxRepository;

    @BeforeEach
    void setUp() throws Exception {
        service = new HrmsNotificationService();
        notificationRepository = Mockito.mock(HrmsNotificationRepository.class);
        preferenceRepository = Mockito.mock(HrmsNotificationPreferenceRepository.class);
        outboxRepository = Mockito.mock(HrmsNotificationOutboxRepository.class);
        set("notificationRepository", notificationRepository);
        set("preferenceRepository", preferenceRepository);
        set("outboxRepository", outboxRepository);
        when(notificationRepository.save(any(HrmsNotification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(preferenceRepository.findByUserIdAndEventType(anyString(), anyString())).thenReturn(Optional.empty());
        when(outboxRepository.existsByEventId(anyString())).thenReturn(false);
    }

    @Test
    void templateRendersPlaceholders_andStripsUnresolved() {
        HrmsNotification n = service.notify("EMP1", "LEAVE_APPROVED",
                Map.of("code", "LV-2026-000123", "from", "2026-12-01", "to", "2026-12-03", "approver", "mgr1"),
                "/leave/my", "LV-2026-000123");
        assertNotNull(n);
        assertEquals("Leave request LV-2026-000123 approved", n.getTitle());
        assertTrue(n.getBody().contains("mgr1"));
        assertFalse(n.getBody().contains("{"), "unresolved placeholders must be stripped");
    }

    @Test
    void emailOutboxEnqueuedOncePerEvent() {
        service.notify("EMP1", "LEAVE_SUBMITTED", Map.of("code", "LV-1"), "/leave", "LV-1");
        verify(outboxRepository, times(1)).save(any());
        verify(outboxRepository, times(1)).existsByEventId("LV-1:LEAVE_SUBMITTED:EMP1");
    }

    @Test
    void preferenceOptOutSuppressesChannels() {
        var pref = new com.mentis.hrms.model.HrmsNotificationPreference();
        pref.setUserId("EMP1");
        pref.setEventType("LEAVE_APPROVED");
        pref.setInApp(false);
        pref.setEmail(false);
        when(preferenceRepository.findByUserIdAndEventType("EMP1", "LEAVE_APPROVED"))
                .thenReturn(Optional.of(pref));
        HrmsNotification n = service.notify("EMP1", "LEAVE_APPROVED", Map.of(), null, "E1");
        assertNull(n, "in-app suppressed by preference");
        verify(outboxRepository, never()).save(any());
    }

    private void set(String field, Object value) throws Exception {
        var f = HrmsNotificationService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(service, value);
    }
}
