package com.mentis.hrms.service;

import com.mentis.hrms.model.OfferWorkflow;
import com.mentis.hrms.repository.OfferWorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;

/**
 * REQ-7-07 — recruitment metrics from VERIFIED tables only (requisition, job_requisition_link,
 * job_applications, application_stage_history, interview, interview_feedback, offer_workflow, application_source).
 * All values are raw aggregates; rendering belongs to the UI.
 */
@Service
public class RecruitmentMetricsService {

    @Autowired private OfferWorkflowRepository offerWorkflowRepository;

    @PersistenceContext
    private EntityManager em;

    public Map<String, Object> all() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("requisitionAgeing", requisitionAgeing());
        out.put("timeToFillByRequisition", timeToFillByRequisition());
        out.put("offerAcceptanceRate", offerAcceptanceRate());
        out.put("feedbackTimelinessHours", feedbackTimelinessHours());
        out.put("sourceQuality", sourceQuality());
        out.put("freshness", java.time.LocalDateTime.now().toString());
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> requisitionAgeing() {
        return nativeRows(
                "SELECT req_code, title, status, DATEDIFF(CURDATE(), created_at) AS ageing_days " +
                "FROM requisition WHERE status IN ('OPEN','ON_HOLD','PENDING_APPROVAL') ORDER BY created_at ASC");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> timeToFillByRequisition() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT r.req_code, r.title, r.created_at, MIN(h.changed_at) AS first_hire, " +
                "       DATEDIFF(MIN(h.changed_at), r.created_at) AS days_to_fill " +
                "FROM requisition r " +
                "JOIN job_requisition_link l ON l.requisition_id = r.id " +
                "JOIN job_applications ja ON ja.job_id = l.job_id " +
                "JOIN application_stage_history h ON h.application_id = ja.id AND h.to_stage = 'HIRED' " +
                "GROUP BY r.id, r.req_code, r.title, r.created_at").getResultList();
        List<Map<String, Object>> perReq = new ArrayList<>();
        double sum = 0;
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reqCode", row[0]);
            m.put("title", row[1]);
            m.put("createdAt", String.valueOf(row[2]));
            m.put("firstHireAt", String.valueOf(row[3]));
            Number days = (Number) row[4];
            m.put("daysToFill", days);
            if (days != null) sum += days.doubleValue();
            perReq.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("perRequisition", perReq);
        out.put("averageDaysToFill", perReq.isEmpty() ? null : sum / perReq.size());
        return out;
    }

    public Map<String, Object> offerAcceptanceRate() {
        long accepted = offerWorkflowRepository.countByStatus(OfferWorkflow.ACCEPTED);
        long sent = offerWorkflowRepository.countByStatus(OfferWorkflow.SENT);
        long declined = offerWorkflowRepository.countByStatus(OfferWorkflow.DECLINED);
        long expired = offerWorkflowRepository.countByStatus(OfferWorkflow.EXPIRED);
        long decided = accepted + declined + expired;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("declined", declined);
        out.put("expired", expired);
        out.put("stillSent", sent);
        out.put("acceptanceRatePct", decided == 0 ? null : (accepted * 100.0 / decided));
        return out;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> feedbackTimelinessHours() {
        List<Object[]> rows = em.createNativeQuery(
                "SELECT AVG(TIMESTAMPDIFF(HOUR, i.scheduled_at, f.submitted_at)) " +
                "FROM interview_feedback f JOIN interview i ON i.id = f.interview_id").getResultList();
        Object avg = rows.isEmpty() ? null : rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("averageHoursFromScheduledToSubmitted", avg);
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> sourceQuality() {
        return nativeRows(
                "SELECT COALESCE(s.source, 'UNSPECIFIED') AS source, " +
                "       COUNT(DISTINCT s.application_id) AS applications, " +
                "       COUNT(DISTINCT CASE WHEN h.to_stage = 'HIRED' THEN s.application_id END) AS hires " +
                "FROM application_source s " +
                "LEFT JOIN application_stage_history h ON h.application_id = s.application_id AND h.to_stage = 'HIRED' " +
                "GROUP BY COALESCE(s.source, 'UNSPECIFIED') ORDER BY applications DESC");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> nativeRows(String sql) {
        List<Object[]> rows = em.createNativeQuery(sql).getResultList();
        List<Map<String, Object>> out = new ArrayList<>();
        String[] keys = null;
        // Column aliases — read back from metadata is not available for native queries; use known key order.
        String lower = sql.toLowerCase();
        if (lower.contains("req_code")) keys = new String[]{"reqCode", "title", "status", "ageingDays"};
        else if (lower.contains("source")) keys = new String[]{"source", "applications", "hires"};
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 0; i < row.length; i++) {
                m.put(keys != null && i < keys.length ? keys[i] : "col" + i, row[i]);
            }
            out.add(m);
        }
        return out;
    }
}
