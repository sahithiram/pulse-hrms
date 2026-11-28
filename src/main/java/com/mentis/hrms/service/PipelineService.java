package com.mentis.hrms.service;

import com.mentis.hrms.model.ApplicationStageHistory;
import com.mentis.hrms.repository.ApplicationStageHistoryRepository;
import com.mentis.hrms.util.PipelineTransitionRules;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;

/**
 * REQ-7-02 — transitions, history append, legacy status dual-write.
 * Current stage = last history entry, else mapped from legacy job_applications.status.
 */
@Service
public class PipelineService {

    @Autowired private ApplicationStageHistoryRepository historyRepository;

    @PersistenceContext
    private EntityManager em;

    public String currentStage(Long applicationId) {
        List<ApplicationStageHistory> hist = historyRepository.findByApplicationIdOrderByChangedAtAsc(applicationId);
        if (!hist.isEmpty()) return hist.get(hist.size() - 1).getToStage();
        Object legacy = em.createNativeQuery("SELECT status FROM job_applications WHERE id = ?")
                .setParameter(1, applicationId).getResultList().stream().findFirst().orElse(null);
        return PipelineTransitionRules.mapLegacyStatus(legacy == null ? null : legacy.toString());
    }

    @Transactional
    public ApplicationStageHistory transition(Long applicationId, String toStage, String changedBy, String reason) {
        String to = toStage == null ? "" : toStage.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        if (!PipelineTransitionRules.isKnown(to)) {
            throw new IllegalArgumentException("Unknown stage: " + toStage);
        }
        String from = currentStage(applicationId);
        if (!PipelineTransitionRules.isAllowed(from, to)) {
            throw new IllegalStateException("Illegal transition " + from + " -> " + to);
        }
        if (PipelineTransitionRules.REJECTED.equals(to) && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Rejection requires a reason");
        }
        ApplicationStageHistory h = new ApplicationStageHistory();
        h.setApplicationId(applicationId);
        h.setFromStage(from);
        h.setToStage(to);
        h.setChangedBy(changedBy);
        h.setChangedAt(LocalDateTime.now());
        h.setReason(reason);
        historyRepository.save(h);
        // Dual-write legacy free-text status so existing screens keep working.
        em.createNativeQuery("UPDATE job_applications SET status = ? WHERE id = ?")
                .setParameter(1, PipelineTransitionRules.humanLabel(to))
                .setParameter(2, applicationId)
                .executeUpdate();
        return h;
    }

    public List<ApplicationStageHistory> history(Long applicationId) {
        return historyRepository.findByApplicationIdOrderByChangedAtAsc(applicationId);
    }
}
