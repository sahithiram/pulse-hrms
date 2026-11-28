package com.mentis.hrms.service;

import com.mentis.hrms.model.OnboardingTask;
import com.mentis.hrms.repository.OnboardingTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** REQ-8-01 — task actions with evidence capture. */
@Service
public class OnboardingTaskService {

    @Autowired private OnboardingTaskRepository taskRepository;

    @Transactional
    public OnboardingTask complete(Long taskId, String completedBy, String evidence) {
        OnboardingTask task = mustGet(taskId);
        if ("DONE".equals(task.getStatus())) {
            return task; // idempotent
        }
        task.setStatus("DONE");
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(completedBy);
        if (evidence != null) task.setEvidence(evidence);
        return taskRepository.save(task);
    }

    @Transactional
    public OnboardingTask skip(Long taskId, String skippedBy, String reason) {
        OnboardingTask task = mustGet(taskId);
        if (task.isMandatory() && (reason == null || reason.trim().isEmpty())) {
            throw new IllegalArgumentException("Skipping a mandatory task requires a reason");
        }
        task.setStatus("SKIPPED");
        task.setCompletedAt(LocalDateTime.now());
        task.setCompletedBy(skippedBy);
        task.setEvidence(reason == null ? "SKIPPED" : "SKIPPED: " + reason);
        return taskRepository.save(task);
    }

    @Transactional
    public OnboardingTask setInProgress(Long taskId) {
        OnboardingTask task = mustGet(taskId);
        if ("PENDING".equals(task.getStatus())) {
            task.setStatus("IN_PROGRESS");
            return taskRepository.save(task);
        }
        return task;
    }

    private OnboardingTask mustGet(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Onboarding task not found: " + taskId));
    }
}
