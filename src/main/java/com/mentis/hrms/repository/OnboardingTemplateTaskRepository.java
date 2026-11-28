package com.mentis.hrms.repository;

import com.mentis.hrms.model.OnboardingTemplateTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OnboardingTemplateTaskRepository extends JpaRepository<OnboardingTemplateTask, Long> {
    List<OnboardingTemplateTask> findByTemplateIdOrderByPositionAsc(Long templateId);
}
