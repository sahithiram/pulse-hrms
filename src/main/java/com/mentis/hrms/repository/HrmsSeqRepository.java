package com.mentis.hrms.repository;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Sequence allocator over hrms_seq (SELECT ... FOR UPDATE) — used for RQ-YYYY-NNNNNN and EMP-YYYY-NNNNNN. */
@Repository
public class HrmsSeqRepository {

    @PersistenceContext
    private EntityManager em;

    public long next(String name) {
        Number n = (Number) em.createNativeQuery("SELECT next_val FROM hrms_seq WHERE name = ? FOR UPDATE")
                .setParameter(1, name)
                .getSingleResult();
        em.createNativeQuery("UPDATE hrms_seq SET next_val = next_val + 1 WHERE name = ?")
                .setParameter(1, name)
                .executeUpdate();
        return n.longValue();
    }
}
