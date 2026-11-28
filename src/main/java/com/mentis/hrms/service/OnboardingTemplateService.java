package com.mentis.hrms.service;

import com.mentis.hrms.model.OnboardingTask;
import com.mentis.hrms.model.OnboardingTemplate;
import com.mentis.hrms.model.OnboardingTemplateTask;
import com.mentis.hrms.repository.OnboardingTaskRepository;
import com.mentis.hrms.repository.OnboardingTemplateRepository;
import com.mentis.hrms.repository.OnboardingTemplateTaskRepository;
import com.mentis.hrms.util.LifecycleRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * REQ-8-01 — onboarding templates (by department/designation) + instantiation of per-employee checklists.
 * Seed: call {@link #seedDefaultTemplate(String)} once (idempotent — skips when a wildcard template exists).
 */
@Service
public class OnboardingTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(OnboardingTemplateService.class);

    @Autowired private OnboardingTemplateRepository templateRepository;
    @Autowired private OnboardingTemplateTaskRepository templateTaskRepository;
    @Autowired private OnboardingTaskRepository taskRepository;

    @Transactional
    public OnboardingTemplate seedDefaultTemplate(String createdBy) {
        Optional<OnboardingTemplate> existing = templateRepository
                .findFirstByActiveTrueAndDepartmentIsNullAndDesignationIsNull();
        if (existing.isPresent()) {
            logger.info("Default onboarding template already exists (id={}), skipping seed", existing.get().getId());
            return existing.get();
        }
        OnboardingTemplate template = new OnboardingTemplate();
        template.setName("Default onboarding checklist");
        template.setCreatedBy(createdBy);
        template = templateRepository.save(template);
        for (String[] seed : LifecycleRules.defaultSeedTasks()) {
            OnboardingTemplateTask t = new OnboardingTemplateTask();
            t.setTemplateId(template.getId());
            t.setTitle(seed[0]);
            t.setOwnerRole(seed[1]);
            t.setDueOffsetDays(Integer.parseInt(seed[2]));
            t.setMandatory("1".equals(seed[3]));
            t.setPosition(Integer.parseInt(seed[4]));
            templateTaskRepository.save(t);
        }
        return template;
    }

    @Transactional
    public List<OnboardingTask> instantiateForEmployee(String employeeId, String department, String designation, LocalDate startDate) {
        List<OnboardingTemplate> candidates = templateRepository.findByActiveTrue();
        OnboardingTemplate match = null;
        if (department != null) {
            match = candidates.stream()
                    .filter(t -> t.getDepartment() != null && t.getDesignation() != null
                            && t.getDepartment().equalsIgnoreCase(department) && t.getDesignation().equalsIgnoreCase(designation))
                    .findFirst().orElse(null);
            if (match == null) {
                match = candidates.stream()
                        .filter(t -> t.getDepartment() != null && t.getDesignation() == null
                                && t.getDepartment().equalsIgnoreCase(department))
                        .findFirst().orElse(null);
            }
        }
        if (match == null) {
            match = candidates.stream()
                    .filter(t -> t.getDepartment() == null && t.getDesignation() == null)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No onboarding template found (not even a wildcard). Run seedDefaultTemplate() first."));
        }
        LocalDate start = startDate != null ? startDate : LocalDate.now();
        List<OnboardingTask> created = new ArrayList<>();
        for (OnboardingTemplateTask tt : templateTaskRepository.findByTemplateIdOrderByPositionAsc(match.getId())) {
            OnboardingTask task = new OnboardingTask();
            task.setEmployeeId(employeeId);
            task.setTemplateTaskId(tt.getId());
            task.setTitle(tt.getTitle());
            task.setOwnerRole(tt.getOwnerRole());
            task.setDueDate(start.plusDays(tt.getDueOffsetDays()));
            task.setMandatory(tt.isMandatory());
            task.setStatus("PENDING");
            created.add(taskRepository.save(task));
        }
        return created;
    }

    public List<OnboardingTask> tasks(String employeeId) {
        return taskRepository.findByEmployeeIdOrderByDueDateAscIdAsc(employeeId);
    }

    public long mandatoryPending(String employeeId) {
        return taskRepository.countByEmployeeIdAndMandatoryTrueAndStatusNotIn(employeeId, List.of("DONE", "SKIPPED"));
    }
}
