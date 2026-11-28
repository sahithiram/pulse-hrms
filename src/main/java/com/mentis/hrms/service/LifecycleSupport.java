package com.mentis.hrms.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Shared native-SQL helpers for lifecycle flows. The Employee ENTITY does NOT map the V3 columns
 * (verified on Drive), so every lifecycle read/write goes through SQL here — never via EmployeeRepository
 * for those columns.
 */
@Component
public class LifecycleSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> employeeByEmployeeId(String employeeId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, employee_id, first_name, last_name, email, department, designation, " +
                "employment_type, employment_mode, employment_status, probation_end_date, confirmation_date, " +
                "notice_start_date, exit_date, exit_reason, start_date, manager_employee_id, " +
                "onboarding_status, onboarding_start_date, onboarding_completed_date, active, is_active " +
                "FROM employees WHERE employee_id = ? LIMIT 1", employeeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** employees.id (PK) of an employee by business id, or null. */
    public Long employeePk(String employeeId) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM employees WHERE employee_id = ? LIMIT 1",
                rs -> rs.next() ? List.of(rs.getLong(1)) : List.of(), employeeId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Business id (employees.employee_id) of a manager PK, or null. */
    public String employeeIdOfPk(Long pk) {
        if (pk == null) return null;
        List<String> rows = jdbcTemplate.query(
                "SELECT employee_id FROM employees WHERE id = ? LIMIT 1",
                rs -> rs.next() ? List.of(rs.getString(1)) : List.of(), pk);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Manager's business employee_id for the given employee business id, or null. */
    public String managerEmployeeIdOf(String employeeId) {
        Map<String, Object> emp = employeeByEmployeeId(employeeId);
        if (emp == null) return null;
        Object mgr = emp.get("manager_employee_id");
        if (mgr instanceof Number n) {
            return employeeIdOfPk(n.longValue());
        }
        return null;
    }

    public void setEmploymentStatus(Long employeePk, String status, LocalDate exitDate, String exitReason) {
        jdbcTemplate.update(
                "UPDATE employees SET employment_status = ?, " +
                "exit_date = COALESCE(?, exit_date), exit_reason = COALESCE(?, exit_reason) WHERE id = ?",
                status, exitDate, exitReason, employeePk);
    }

    public void setConfirmed(Long employeePk, LocalDate confirmationDate) {
        jdbcTemplate.update(
                "UPDATE employees SET employment_status = 'CONFIRMED', confirmation_date = ? WHERE id = ?",
                confirmationDate, employeePk);
    }

    public void setProbationEndDate(Long employeePk, LocalDate newEndDate) {
        jdbcTemplate.update("UPDATE employees SET probation_end_date = ? WHERE id = ?", newEndDate, employeePk);
    }

    public void setNoticeStarted(Long employeePk, LocalDate noticeStart) {
        jdbcTemplate.update(
                "UPDATE employees SET employment_status = 'NOTICE', notice_start_date = ? WHERE id = ?",
                noticeStart, employeePk);
    }

    public void setInactive(Long employeePk, LocalDate exitDate, String exitReason) {
        jdbcTemplate.update(
                "UPDATE employees SET employment_status = 'INACTIVE', exit_date = ?, exit_reason = ?, " +
                "active = 0, is_active = 0 WHERE id = ?",
                exitDate, exitReason, employeePk);
    }

    public void setExited(Long employeePk, LocalDate exitDate, String exitReason) {
        jdbcTemplate.update(
                "UPDATE employees SET employment_status = 'EXITED', exit_date = ?, exit_reason = ?, " +
                "active = 0, is_active = 0 WHERE id = ?",
                exitDate, exitReason, employeePk);
    }

    /** effective-dated audit row — required NOT NULLs: effective_from, change_reason. */
    public void jobHistory(Long employeePk, String changeReason, LocalDate effectiveFrom, String changedByUser) {
        Long actorPk = null;
        if (changedByUser != null && !changedByUser.isBlank()) {
            try {
                actorPk = employeePk(changedByUser);
            } catch (Exception ignored) {
                actorPk = null; // best effort — audit must not block the transition
            }
        }
        jdbcTemplate.update(
                "INSERT INTO employee_job_history (employee_id, effective_from, change_reason, changed_by) " +
                "VALUES (?,?,?,?)",
                employeePk, effectiveFrom, changeReason, actorPk);
    }

    public int directReportCount(Long managerPk) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE manager_employee_id = ? AND active = 1",
                Integer.class, managerPk);
        return count == null ? 0 : count;
    }
}
