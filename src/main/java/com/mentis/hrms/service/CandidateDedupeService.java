package com.mentis.hrms.service;

import com.mentis.hrms.model.ApplicationMeta;
import com.mentis.hrms.repository.ApplicationMetaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REQ-7-05 — duplicate detection by email / phone / resume hash; talent pool flag; rejection reasons.
 * job_applications has email + phone (verified); resume_hash + phone_normalized live in application_meta.
 */
@Service
public class CandidateDedupeService {

    /** Typed native-query helper: jakarta Query#getResultList returns a raw List (erasure) — cast once, safely. */
    private List<Map<String, Object>> nativeRows(String sql, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = (List<Object[]>) q.getResultList();
        return rows.stream().map(this::rowMap).collect(Collectors.toList());
    }


    @Autowired private ApplicationMetaRepository metaRepository;

    @PersistenceContext
    private EntityManager em;

    public static String normalizePhone(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 10) return digits.isEmpty() ? null : digits;
        return digits.substring(digits.length() - 10);
    }

    /** Everything matching the given email, phone and/or resume hash across job_applications (+ meta). */
    @SuppressWarnings("unchecked")
    public Map<String, List<Map<String, Object>>> findDuplicates(String email, String phone, String resumeHash) {
        Map<String, List<Map<String, Object>>> out = new LinkedHashMap<>();
        if (email != null && !email.isBlank()) {
            out.put("email", nativeRows(
                    "SELECT id, first_name, last_name, email, job_id, status FROM job_applications WHERE lower(email) = lower(?)",
                    email.trim()));
        }
        String norm = normalizePhone(phone);
        if (norm != null) {
            List<Map<String, Object>> rows = metaRepository.findByPhoneNormalized(norm).stream().map(m -> {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("applicationId", m.getApplicationId());
                map.put("resumeHash", m.getResumeHash());
                return map;
            }).collect(Collectors.toList());
            out.put("phone", rows);
        }
        if (resumeHash != null && !resumeHash.isBlank()) {
            out.put("resumeHash", metaRepository.findByResumeHash(resumeHash.trim()).stream()
                    .map(m -> Map.<String, Object>of("applicationId", m.getApplicationId(), "phoneNormalized", String.valueOf(m.getPhoneNormalized())))
                    .collect(Collectors.toList()));
        }
        return out;
    }

    @Transactional
    public ApplicationMeta upsertMeta(Long applicationId, String resumeHash, String phone, String rejectionReason, Boolean talentPool) {
        ApplicationMeta meta = metaRepository.findById(applicationId).orElseGet(() -> {
            ApplicationMeta m = new ApplicationMeta();
            m.setApplicationId(applicationId);
            return m;
        });
        if (resumeHash != null) meta.setResumeHash(resumeHash);
        if (phone != null) meta.setPhoneNormalized(normalizePhone(phone));
        if (rejectionReason != null) meta.setRejectionReason(rejectionReason);
        if (talentPool != null) meta.setTalentPool(talentPool);
        return metaRepository.save(meta);
    }

    public List<Map<String, Object>> retentionCandidates(int limit) {
        return nativeRows(
                "SELECT application_id, retention_until FROM candidate_consent WHERE retention_until IS NOT NULL AND retention_until < CURDATE() LIMIT ?",
                limit);
    }

    private Map<String, Object> rowMap(Object row) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (row instanceof Object[] arr) {
            for (int i = 0; i < arr.length; i++) map.put("col" + i, arr[i]);
        } else {
            map.put("value", row);
        }
        return map;
    }
}
