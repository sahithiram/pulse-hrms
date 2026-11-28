package com.mentis.hrms.repository;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;

/** REQ-6-10 attendance register — reads the Sprint 5 summary table, never raw punches. */
@Repository
public class AttendanceRegisterReadRepository {

    @PersistenceContext
    private EntityManager em;

    @SuppressWarnings("unchecked")
    public List<Object[]> register(LocalDate from, LocalDate to) {
        return em.createNativeQuery(
            "SELECT m.employee_id, COALESCE(e.employee_name, TRIM(CONCAT_WS(' ', e.first_name, e.last_name))) AS name, " +
            "       m.metrics_date, m.status, m.net_minutes, m.late_minutes, m.overtime_minutes, m.is_regularized " +
            "FROM employee_daily_metrics m LEFT JOIN employees e ON e.employee_id = m.employee_id " +
            "WHERE m.metrics_date BETWEEN :from AND :to " +
            "ORDER BY m.employee_id, m.metrics_date")
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
    }
}
