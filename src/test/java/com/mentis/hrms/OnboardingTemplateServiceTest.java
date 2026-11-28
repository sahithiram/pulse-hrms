package com.mentis.hrms;

import com.mentis.hrms.model.OnboardingTask;
import com.mentis.hrms.model.OnboardingTemplate;
import com.mentis.hrms.model.OnboardingTemplateTask;
import com.mentis.hrms.repository.OnboardingTaskRepository;
import com.mentis.hrms.repository.OnboardingTemplateRepository;
import com.mentis.hrms.repository.OnboardingTemplateTaskRepository;
import com.mentis.hrms.service.OnboardingTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** NOT executed yet — run with: mvn -Dtest=OnboardingTemplateServiceTest test */
class OnboardingTemplateServiceTest {

    private OnboardingTemplateService service;
    private OnboardingTemplateRepository templateRepository;
    private OnboardingTemplateTaskRepository templateTaskRepository;
    private OnboardingTaskRepository taskRepository;

    @BeforeEach
    void setUp() throws Exception {
        service = new OnboardingTemplateService();
        templateRepository = mock(OnboardingTemplateRepository.class);
        templateTaskRepository = mock(OnboardingTemplateTaskRepository.class);
        taskRepository = mock(OnboardingTaskRepository.class);
        setField(service, "templateRepository", templateRepository);
        setField(service, "templateTaskRepository", templateTaskRepository);
        setField(service, "taskRepository", taskRepository);
    }

    private static void setField(Object target, String field, Object value) throws Exception {
        var f = OnboardingTemplateService.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    /**
     * FIX: OnboardingTemplate has no setId(...). Set the "id" field reflectively instead,
     * walking up superclasses in case it lives in a @MappedSuperclass.
     */
    private static void setId(Object target, Long id) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("id");
                f.setAccessible(true);
                f.set(target, id);
                return;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("No 'id' field found on " + target.getClass().getName());
    }

    @Test
    void instantiateCreatesTasksFromWildcardTemplateWithOffsets() throws Exception {
        OnboardingTemplate wildcard = new OnboardingTemplate();
        wildcard.setName("Default");
        setId(wildcard, 42L);   // FIX: was wildcard.setId(42L)
        when(templateRepository.findByActiveTrue()).thenReturn(List.of(wildcard));

        List<OnboardingTemplateTask> templateTasks = new ArrayList<>();
        for (String[] seed : com.mentis.hrms.util.LifecycleRules.defaultSeedTasks()) {
            OnboardingTemplateTask tt = new OnboardingTemplateTask();
            tt.setTemplateId(42L);
            tt.setTitle(seed[0]);
            tt.setOwnerRole(seed[1]);
            tt.setDueOffsetDays(Integer.parseInt(seed[2]));
            tt.setMandatory("1".equals(seed[3]));
            tt.setPosition(Integer.parseInt(seed[4]));
            templateTasks.add(tt);
        }
        when(templateTaskRepository.findByTemplateIdOrderByPositionAsc(42L)).thenReturn(templateTasks);
        when(taskRepository.save(any(OnboardingTask.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate start = LocalDate.of(2026, 3, 2);
        List<OnboardingTask> created = service.instantiateForEmployee("EMP1001", "Engineering", "Developer", start);

        assertEquals(9, created.size());
        ArgumentCaptor<OnboardingTask> captor = ArgumentCaptor.forClass(OnboardingTask.class);
        verify(taskRepository, times(9)).save(captor.capture());
        List<OnboardingTask> saved = captor.getAllValues();
        assertEquals("PENDING", saved.get(0).getStatus());
        assertEquals(start.plusDays(1), saved.get(0).getDueDate());   // documents due start+1
        assertEquals(start.plusDays(7), saved.get(8).getDueDate());   // first-week meeting start+7
        assertTrue(saved.stream().allMatch(t -> "EMP1001".equals(t.getEmployeeId())));
    }

    @Test
    void instantiateWithoutAnyTemplateThrows() {
        when(templateRepository.findByActiveTrue()).thenReturn(List.of());
        assertThrows(IllegalStateException.class,
                () -> service.instantiateForEmployee("EMP1001", null, null, LocalDate.now()));
    }
}