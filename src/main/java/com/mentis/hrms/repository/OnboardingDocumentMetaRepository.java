package com.mentis.hrms.repository;

import com.mentis.hrms.model.OnboardingDocumentMeta;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface OnboardingDocumentMetaRepository extends JpaRepository<OnboardingDocumentMeta, Long> {
    List<OnboardingDocumentMeta> findByExpiresOnBeforeAndRemindSentIsNull(LocalDate horizon);
    List<OnboardingDocumentMeta> findByExpiresOnBetweenAndRemindSentIsNull(LocalDate from, LocalDate to);
}
