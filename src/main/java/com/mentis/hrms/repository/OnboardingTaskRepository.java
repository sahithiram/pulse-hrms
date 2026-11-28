package com.mentis.hrms.repository;

import com.mentis.hrms.model.OnboardingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OnboardingTaskRepository extends JpaRepository<OnboardingTask, Long> {
    List<OnboardingTask> findByEmployeeIdOrderByDueDateAscIdAsc(String employeeId);
    long countByEmployeeIdAndMandatoryTrueAndStatusNotIn(String employeeId, List<String> doneStatuses);
}
