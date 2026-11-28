-- V3 — Sprint 1: employee master, org structure, effective-dated history.
-- Forward-only. Safe on a database already at V1(baseline)+V2(announcements).
-- Legacy text columns are KEPT for one release (deprecated): department, designation,
-- work_location, manager, salary. Nothing below drops or rewrites them.

-- ============ 1) Typed org + lifecycle columns on employees ============
ALTER TABLE employees
  ADD COLUMN department_id         BIGINT NULL,
  ADD COLUMN designation_id        BIGINT NULL,
  ADD COLUMN location_id           BIGINT NULL,
  ADD COLUMN manager_employee_id   BIGINT NULL,
  ADD COLUMN secondary_manager_id  BIGINT NULL,
  ADD COLUMN employment_status     VARCHAR(20) NOT NULL DEFAULT 'PROBATION',
  ADD COLUMN employment_mode       VARCHAR(20) NOT NULL DEFAULT 'FULL_TIME',
  ADD COLUMN work_mode             VARCHAR(20) NOT NULL DEFAULT 'OFFICE',
  ADD COLUMN probation_end_date    DATE NULL,
  ADD COLUMN confirmation_date     DATE NULL,
  ADD COLUMN notice_start_date     DATE NULL,
  ADD COLUMN exit_date             DATE NULL,
  ADD COLUMN exit_reason           VARCHAR(255) NULL,
  ADD COLUMN salary_amount         DECIMAL(15,2) NULL,
  ADD CONSTRAINT fk_emp_department  FOREIGN KEY (department_id) REFERENCES departments(id),
  ADD CONSTRAINT fk_emp_designation FOREIGN KEY (designation_id) REFERENCES designations(id),
  ADD CONSTRAINT fk_emp_location    FOREIGN KEY (location_id) REFERENCES locations(id),
  ADD CONSTRAINT fk_emp_manager     FOREIGN KEY (manager_employee_id) REFERENCES employees(id),
  ADD CONSTRAINT fk_emp_sec_manager FOREIGN KEY (secondary_manager_id) REFERENCES employees(id);

ALTER TABLE employees
  ADD CONSTRAINT chk_emp_status CHECK (employment_status IN ('ONBOARDING','PROBATION','CONFIRMED','NOTICE','EXITED','INACTIVE')),
  ADD CONSTRAINT chk_emp_mode   CHECK (employment_mode  IN ('FULL_TIME','PART_TIME','CONTRACT','INTERN')),
  ADD CONSTRAINT chk_emp_workmode CHECK (work_mode      IN ('OFFICE','HYBRID','REMOTE'));

-- Case-insensitive unique email + org hot-path indexes + lifecycle index
CREATE UNIQUE INDEX uq_employees_email_ci ON employees (email) /* MySQL: emails stored as-typed; uniqueness enforced at service layer + CI check in backfill */;
CREATE INDEX idx_emp_department  ON employees (department_id);
CREATE INDEX idx_emp_manager     ON employees (manager_employee_id);
CREATE INDEX idx_emp_status      ON employees (employment_status);

-- ============ 2) Effective-dated history tables ============
CREATE TABLE employee_job_history (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id     BIGINT NOT NULL,                       -- employees.id (typed FK, unlike legacy strings)
  department_id   BIGINT NULL,
  designation_id  BIGINT NULL,
  location_id     BIGINT NULL,
  manager_employee_id BIGINT NULL,
  employment_mode VARCHAR(20) NULL,
  work_mode       VARCHAR(20) NULL,
  effective_from  DATE NOT NULL,
  effective_to    DATE NULL,                             -- NULL = current
  change_reason   VARCHAR(255) NOT NULL,
  changed_by      BIGINT NULL,                           -- employees.id of the actor
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ejh_employee  FOREIGN KEY (employee_id)  REFERENCES employees(id),
  CONSTRAINT fk_ejh_dept      FOREIGN KEY (department_id) REFERENCES departments(id),
  CONSTRAINT fk_ejh_desig     FOREIGN KEY (designation_id) REFERENCES designations(id),
  CONSTRAINT fk_ejh_loc       FOREIGN KEY (location_id) REFERENCES locations(id),
  CONSTRAINT fk_ejh_manager   FOREIGN KEY (manager_employee_id) REFERENCES employees(id),
  INDEX idx_ejh_emp_from (employee_id, effective_from),
  INDEX idx_ejh_effective (effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employee_salary_history (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id     BIGINT NOT NULL,
  amount          DECIMAL(15,2) NOT NULL,
  currency        CHAR(3) NOT NULL,
  effective_from  DATE NOT NULL,
  effective_to    DATE NULL,
  reason          VARCHAR(255) NOT NULL,
  approved_by     BIGINT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_esh_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
  CONSTRAINT chk_esh_amount CHECK (amount >= 0),
  INDEX idx_esh_emp_from (employee_id, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ 3) Delegation (manager going on leave) ============
CREATE TABLE employee_delegate (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  delegator_id   BIGINT NOT NULL,
  delegate_id    BIGINT NOT NULL,
  from_date      DATE NOT NULL,
  to_date        DATE NOT NULL,
  scope          VARCHAR(30) NOT NULL,                    -- LEAVE_APPROVAL | ATTENDANCE_APPROVAL | ALL
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_del_del  FOREIGN KEY (delegator_id) REFERENCES employees(id),
  CONSTRAINT fk_del_to   FOREIGN KEY (delegate_id) REFERENCES employees(id),
  CONSTRAINT chk_del_scope CHECK (scope IN ('LEAVE_APPROVAL','ATTENDANCE_APPROVAL','ALL')),
  CONSTRAINT chk_del_dates CHECK (to_date >= from_date),
  INDEX idx_delegate_active (delegate_id, from_date, to_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ 4) Migration issue punch-list (BackfillService) ============
CREATE TABLE data_migration_issues (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  entity_type    VARCHAR(30) NOT NULL,                    -- DEPARTMENT | DESIGNATION | LOCATION | MANAGER | SALARY | EMAIL | EMPLOYMENT_STATUS
  employee_ref   VARCHAR(100) NOT NULL,                   -- employees.employee_id (business key)
  field_name     VARCHAR(50) NOT NULL,
  raw_value      VARCHAR(500) NULL,
  issue          VARCHAR(255) NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',     -- OPEN | RESOLVED | IGNORED
  resolution     VARCHAR(500) NULL,
  checksum       CHAR(64) NOT NULL,                       -- sha-256 of (entity_type|employee_ref|field_name|raw_value|issue); unique → idempotent reruns
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  resolved_at    DATETIME NULL,
  resolved_by    VARCHAR(100) NULL,
  CONSTRAINT uq_dmi_checksum UNIQUE (checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============ 5) Seed employment_status from known onboarding/start state ============
-- Best-effort only; rows that cannot be classified stay 'PROBATION' and are listed
-- to HR via the backfill report — NOT guessed.
UPDATE employees e
SET employment_status = CASE
  WHEN e.onboarding_status IS NOT NULL AND e.onboarding_status = 'COMPLETED' THEN 'PROBATION'
  WHEN e.onboarding_status IS NOT NULL AND e.onboarding_status IN ('NOT_STARTED','IN_PROGRESS') THEN 'ONBOARDING'
  ELSE 'PROBATION' END
WHERE employment_status = 'PROBATION';
