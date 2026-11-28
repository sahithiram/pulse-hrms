-- Sprint 8: Onboarding depth, probation & confirmation, offboarding.
-- PREREQUISITES: Sprints 0..7. Verified legacy tables used (NO alters): onboarding_documents, employees (V3 cols),
-- leave_ledger, leave_policy, leave_type, leave_approval_step (Sprint 4), employee_job_history (V3), audit_log (V4).
-- V3 lifecycle columns (employment_status/probation_end_date/confirmation_date/notice_start_date/exit_date/exit_reason/
--   salary_amount/employment_mode/manager_employee_id/employment_type) are written via native SQL — the Employee
--   ENTITY does not map them (verified on Drive).
-- Rename migration number if V10 is taken.

CREATE TABLE onboarding_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(128) NOT NULL,
  department VARCHAR(100) NULL,                  -- NULL = wildcard
  designation VARCHAR(120) NULL,                 -- NULL = wildcard
  active TINYINT(1) NOT NULL DEFAULT 1,
  created_by VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE onboarding_template_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  owner_role VARCHAR(30) NOT NULL,               -- EMPLOYEE|MANAGER|IT|HR|ADMIN|BUDDY
  due_offset_days INT NOT NULL DEFAULT 0,        -- due date = onboarding start + offset
  mandatory TINYINT(1) NOT NULL DEFAULT 1,
  position INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_ott_template FOREIGN KEY (template_id) REFERENCES onboarding_template(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE onboarding_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,              -- business key (matches metrics/ledger pattern)
  template_task_id BIGINT NULL,
  title VARCHAR(255) NOT NULL,
  owner_role VARCHAR(30) NOT NULL,
  owner_id VARCHAR(50) NULL,
  due_date DATE NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING', -- PENDING|IN_PROGRESS|DONE|SKIPPED
  mandatory TINYINT(1) NOT NULL DEFAULT 1,
  completed_at DATETIME NULL,
  completed_by VARCHAR(50) NULL,
  evidence VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_otask_emp (employee_id, status),
  INDEX idx_otask_due (due_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE onboarding_document_meta (          -- REQ-8-02: expiry tracking for expiring documents
  document_id BIGINT PRIMARY KEY,
  expires_on DATE NULL,
  remind_sent DATETIME NULL,
  CONSTRAINT fk_odm_doc FOREIGN KEY (document_id) REFERENCES onboarding_documents(id) ON DELETE CASCADE,
  INDEX idx_odm_expires (expires_on, remind_sent)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE onboarding_feedback (               -- REQ-8-04: day-7 / day-30 survey; visible to HR
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  day INT NOT NULL,                              -- 7 or 30
  ratings_json TEXT NULL,
  comments TEXT NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_onb_fb UNIQUE (employee_id, day),
  CONSTRAINT chk_onb_fb_day CHECK (day IN (7, 30))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE probation_review (                  -- REQ-8-06 (auto-created 30 days before probation_end_date)
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  employee_pk BIGINT NULL,                       -- employees.id when known
  due_date DATE NOT NULL,
  period_end DATE NULL,                          -- probation_end_date at decision time
  manager_rating INT NULL,                       -- 1..5
  competency_ratings TEXT NULL,                  -- JSON
  recommendation VARCHAR(12) NULL,                -- CONFIRM|EXTEND|TERMINATE
  extension_days INT NULL,
  comments TEXT NULL,
  hr_decision VARCHAR(12) NULL,                  -- APPROVED|REJECTED
  hr_decider VARCHAR(50) NULL,
  hr_decided_at DATETIME NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING', -- PENDING|DECIDED
  reminder_30_sent TINYINT(1) NOT NULL DEFAULT 0,
  reminder_14_sent TINYINT(1) NOT NULL DEFAULT 0,
  reminder_7_sent TINYINT(1) NOT NULL DEFAULT 0,
  escalation_sent TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_pr_rating CHECK (manager_rating IS NULL OR (manager_rating BETWEEN 1 AND 5)),
  CONSTRAINT chk_pr_rec CHECK (recommendation IS NULL OR recommendation IN ('CONFIRM','EXTEND','TERMINATE')),
  CONSTRAINT chk_pr_hr CHECK (hr_decision IS NULL OR hr_decision IN ('APPROVED','REJECTED')),
  INDEX idx_pr_emp (employee_id, status),
  INDEX idx_pr_due (due_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notice_rule (                       -- OD-09 seed; HR to confirm values — change rows, not code
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employment_type VARCHAR(20) NOT NULL,          -- FULL_TIME|PART_TIME|CONTRACT|INTERN
  grade VARCHAR(30) NOT NULL DEFAULT '__ANY__',  -- '__ANY__' = default for type
  notice_days INT NOT NULL,
  CONSTRAINT uq_notice UNIQUE (employment_type, grade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO notice_rule (employment_type, grade, notice_days) VALUES
  ('FULL_TIME', '__ANY__', 30), ('PART_TIME', '__ANY__', 15), ('CONTRACT', '__ANY__', 30), ('INTERN', '__ANY__', 7);

CREATE TABLE resignation (                       -- REQ-8-07
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  submitted_on DATE NOT NULL,
  requested_last_day DATE NOT NULL,
  reason TEXT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'SUBMITTED',  -- SUBMITTED|ACCEPTED|REVOKED|COMPLETED
  accepted_last_day DATE NULL,
  notice_days_required INT NOT NULL,
  notice_shortfall INT NULL,
  notice_buyout TINYINT(1) NOT NULL DEFAULT 0,
  prev_employment_status VARCHAR(20) NULL,          -- captured at accept() for safe revoke
  submitted_by VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked_at DATETIME NULL,
  revoke_reason VARCHAR(255) NULL,
  CONSTRAINT chk_resig_status CHECK (status IN ('SUBMITTED','ACCEPTED','REVOKED','COMPLETED')),
  INDEX idx_resig_status (status, accepted_last_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exit_checklist (                    -- REQ-8-08
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  task VARCHAR(48) NOT NULL,                     -- KNOWLEDGE_TRANSFER|ASSET_RETURN|ACCESS_REMOVAL|LEAVE_ENCASHMENT|DOCUMENT_HANDOVER|MANAGER_REASSIGN|MANAGER_SIGNOFF|HR_SIGNOFF
  owner VARCHAR(30) NOT NULL,                    -- EMPLOYEE|MANAGER|IT|HR
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING', -- PENDING|DONE|NA
  due_date DATE NULL,
  completed_at DATETIME NULL,
  completed_by VARCHAR(50) NULL,
  notes VARCHAR(255) NULL,
  CONSTRAINT uq_exit UNIQUE (employee_id, task),
  INDEX idx_exit_owner (owner, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE exit_interview (                     -- REQ-8-08 exit interview form (JSON body)
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL UNIQUE,
  form_json TEXT NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_policy_confirmation_grant (   -- REQ-8-06: confirmation-coupled entitlements (NO invented numbers — HR seeds)
  leave_policy_id BIGINT PRIMARY KEY,
  grant_days DECIMAL(5,2) NOT NULL,
  note VARCHAR(255) NULL,
  CONSTRAINT fk_lpcg_policy FOREIGN KEY (leave_policy_id) REFERENCES leave_policy(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
