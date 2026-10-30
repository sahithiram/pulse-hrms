package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateTaskRequest;
import com.mentis.hrms.pm.dto.response.TaskResponse;
import com.mentis.hrms.pm.entity.RecurringTaskRule;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.TaskType;
import com.mentis.hrms.pm.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RecurringTaskExecutor {

    private final TaskService taskService;
    private final TaskRepository taskRepo;

    /** @return new task id, or null if this occurrence already exists. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(RecurringTaskRule r, LocalDate occurrence) {
        if (taskRepo.existsByRecurringRuleIdAndRecurringOccurrence(r.getId(), occurrence)) return null;

        TaskResponse t = taskService.createTask(CreateTaskRequest.builder()
                .projectId(r.getProjectId())
                .title(r.getTitle())
                .description(r.getDescription())
                .priority(r.getPriority())
                .type(TaskType.TASK)
                .dueDate(occurrence.plusDays(r.getDueAfterDays()))
                .assigneeIds(r.getAssigneeId() == null ? null : List.of(r.getAssigneeId()))
                .build(), r.getCreatedBy());

        Task task = taskRepo.findById(t.getId()).orElseThrow();
        task.setRecurringRuleId(r.getId());
        task.setRecurringOccurrence(occurrence);
        taskRepo.saveAndFlush(task);          // unique (rule, occurrence) stops duplicates even across two servers
        return t.getId();
    }
}