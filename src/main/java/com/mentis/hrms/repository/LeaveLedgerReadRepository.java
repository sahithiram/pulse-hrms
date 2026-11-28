package com.mentis.hrms.repository;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;

/** Native aggregation for the leave-liability report (bypasses Sprint-4 entity graph deliberately). */
@Repository
public class LeaveLedgerReadRepository {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<Object[]> liabilityByType() {
        return em.createNativeQuery(
            "SELECT t.code, t.name, COALESCE(SUM(l.amount), 0) AS balance_sum, COUNT(DISTINCT l.employee_id) AS employees " +
            "FROM leave_ledger l JOIN leave_type t ON t.id = l.leave_type_id " +
            "GROUP BY t.code, t.name ORDER BY t.code").getResultList();
    }
}
