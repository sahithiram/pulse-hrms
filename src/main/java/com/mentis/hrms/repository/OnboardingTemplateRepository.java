package com.mentis.hrms.repository;

import com.mentis.hrms.model.OnboardingTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OnboardingTemplateRepository extends JpaRepository<OnboardingTemplate, Long> {
    List<OnboardingTemplate> findByActiveTrue();
    Optional<OnboardingTemplate> findFirstByActiveTrueAndDepartmentIgnoreCaseAndDesignationIgnoreCase(String department, String designation);
    Optional<OnboardingTemplate> findFirstByActiveTrueAndDepartmentIgnoreCase(String department);
    Optional<OnboardingTemplate> findFirstByActiveTrueAndDepartmentIsNullAndDesignationIsNull();
}
