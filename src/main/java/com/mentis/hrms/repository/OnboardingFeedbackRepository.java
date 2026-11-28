package com.mentis.hrms.repository;

import com.mentis.hrms.model.OnboardingFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OnboardingFeedbackRepository extends JpaRepository<OnboardingFeedback, Long> {
    Optional<OnboardingFeedback> findByEmployeeIdAndDay(String employeeId, int day);
    List<OnboardingFeedback> findByEmployeeId(String employeeId);
}
