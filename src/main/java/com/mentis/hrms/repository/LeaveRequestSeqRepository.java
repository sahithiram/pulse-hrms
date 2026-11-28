package com.mentis.hrms.repository;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Atomic per-year sequence for human leave codes (LV-YYYY-NNNNNN).
 * Uses INSERT ... ON DUPLICATE KEY UPDATE — safe under concurrent applications.
 */
@Repository
public class LeaveRequestSeqRepository {

    @PersistenceContext
    private EntityManager em;

    public long next(int year) {
        em.createNativeQuery(
            "INSERT INTO leave_request_seq(year, seq) VALUES (:y, 1) " +
            "ON DUPLICATE KEY UPDATE seq = LAST_INSERT_ID(seq + 1)")
           .setParameter("y", year)
           .executeUpdate();
        Number n = (Number) em.createNativeQuery("SELECT LAST_INSERT_ID()").getSingleResult();
        return n.longValue();
    }
}
