# V3 rollback note (documentation only — do NOT auto-run)
DROP TABLE IF EXISTS data_migration_issues, employee_delegate, employee_salary_history, employee_job_history;
ALTER TABLE employees
  DROP FOREIGN KEY fk_emp_department, DROP FOREIGN KEY fk_emp_designation, DROP FOREIGN KEY fk_emp_location,
  DROP FOREIGN KEY fk_emp_manager, DROP FOREIGN KEY fk_emp_sec_manager;
ALTER TABLE employees
  DROP COLUMN department_id, DROP COLUMN designation_id, DROP COLUMN location_id,
  DROP COLUMN manager_employee_id, DROP COLUMN secondary_manager_id,
  DROP COLUMN employment_status, DROP COLUMN employment_mode, DROP COLUMN work_mode,
  DROP COLUMN probation_end_date, DROP COLUMN confirmation_date,
  DROP COLUMN notice_start_date, DROP COLUMN exit_date, DROP COLUMN exit_reason, DROP COLUMN salary_amount;
-- Restore from backup is the supported rollback. Never hand-edit applied migrations.
