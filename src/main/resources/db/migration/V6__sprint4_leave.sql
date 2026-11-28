-- Sprint 4: Leave Management (ledger-based balances)
-- PREREQUISITES: Sprint 0..3 applied. V6 assumes table `leave_requests` and `leave_balance` exist (they do).
-- Rename to the next free migration number if V6 is taken.

CREATE TABLE leave_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(32) NOT NULL UNIQUE,
  name VARCHAR(80) NOT NULL,
  paid TINYINT(1) NOT NULL DEFAULT 1,
  requires_document_after_days INT NULL,
  max_consecutive_days INT NULL,
  min_notice_days INT NOT NULL DEFAULT 0,
  allow_half_day TINYINT(1) NOT NULL DEFAULT 1,
  allow_negative_balance TINYINT(1) NOT NULL DEFAULT 0,
  gender_restriction VARCHAR(16) NULL,             -- MALE|FEMALE|NULL (applies to all)
  applicable_employment_types VARCHAR(64) NULL,     -- CSV e.g. FULL_TIME,INTERN (NULL = all)
  applicable_after_days_of_service INT NULL,
  counts_weekends_and_holidays TINYINT(1) NOT NULL DEFAULT 0,
  is_active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO leave_type (code, name, paid, requires_document_after_days, max_consecutive_days, min_notice_days, allow_half_day, counts_weekends_and_holidays)
SELECT * FROM (
  SELECT 'CL','Casual Leave',1,NULL,3,0,1,0 UNION ALL
  SELECT 'SL','Sick Leave',1,2,NULL,0,1,0 UNION ALL
  SELECT 'EL','Earned/Privilege Leave',1,NULL,NULL,7,1,0 UNION ALL
  SELECT 'ML','Maternity Leave',1,NULL,NULL,30,0,1 UNION ALL
  SELECT 'PL','Paternity Leave',1,NULL,15,0,1,0 UNION ALL
  SELECT 'BL','Bereavement Leave',1,NULL,5,0,1,0 UNION ALL
  SELECT 'CO','Compensatory Off',1,NULL,2,0,1,0 UNION ALL
  SELECT 'LOP','Loss of Pay',0,NULL,NULL,0,1,0 UNION ALL
  SELECT 'OH','Optional Holiday',1,NULL,1,0,1,0
) seed(code,name,paid,req_doc,max_cons,min_notice,half_day,counts_weekends)
WHERE NOT EXISTS (SELECT 1 FROM leave_type);

CREATE TABLE leave_policy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  leave_type_id BIGINT NOT NULL,
  department VARCHAR(64) NULL,
  location_id BIGINT NULL,
  employment_type VARCHAR(32) NULL,
  grade VARCHAR(32) NULL,
  annual_quota DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  accrual_method VARCHAR(16) NOT NULL DEFAULT 'NONE',   -- NONE|MONTHLY|QUARTERLY|YEARLY_UPFRONT
  accrual_day INT NULL,                                  -- day of month accrual posts
  carry_forward_limit DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  carry_forward_expiry_months INT NOT NULL DEFAULT 0,
  encashable TINYINT(1) NOT NULL DEFAULT 0,
  encashment_cap DECIMAL(5,2) NULL,
  probation_rule VARCHAR(16) NOT NULL DEFAULT 'FULL',    -- PRORATA|NONE|FULL
  effective_from DATE NULL,
  effective_to DATE NULL,
  CONSTRAINT fk_lp_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_type(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_leave_policy_type ON leave_policy(leave_type_id);

CREATE TABLE leave_ledger (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  leave_type_id BIGINT NOT NULL,
  entry_date DATE NOT NULL,
  entry_type VARCHAR(32) NOT NULL,      -- CREDIT_ACCRUAL|CREDIT_CARRY_FORWARD|CREDIT_MANUAL|DEBIT_LEAVE|CREDIT_REVERSAL|DEBIT_EXPIRY|DEBIT_ENCASHMENT
  amount DECIMAL(5,2) NOT NULL,         -- +ve credit / -ve debit convention documented in contract; service enforces sign
  reference_type VARCHAR(32) NULL,      -- leave_request|accrual_job|carry_forward_job|manual|cancellation
  reference_id VARCHAR(64) NULL,
  remarks VARCHAR(255) NULL,
  created_by VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ll_leave_type FOREIGN KEY (leave_type_id) REFERENCES leave_type(id),
  UNIQUE KEY uq_ll_idem (reference_type, reference_id, entry_type, leave_type_id, employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ll_emp_type ON leave_ledger(employee_id, leave_type_id);

CREATE TABLE leave_request_seq (
  year INT PRIMARY KEY,
  seq BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_request_day (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(50) NOT NULL,
  day DATE NOT NULL,
  day_fraction DECIMAL(3,2) NOT NULL DEFAULT 1.00,
  counted TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uq_lrd_day UNIQUE (request_id, day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_lrd_request ON leave_request_day(request_id);
CREATE INDEX idx_lrd_day ON leave_request_day(day);

CREATE TABLE leave_approval_step (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id VARCHAR(50) NOT NULL,
  step_no INT NOT NULL,
  approver_id VARCHAR(50) NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|REJECTED|SKIPPED
  acted_at DATETIME NULL,
  remarks VARCHAR(255) NULL,
  CONSTRAINT uq_las_step UNIQUE (request_id, step_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE leave_cancellation_request (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  leave_request_id VARCHAR(50) NOT NULL,
  requested_by VARCHAR(50) NOT NULL,
  reason VARCHAR(500) NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',  -- PENDING|APPROVED|REJECTED
  partial_days VARCHAR(500) NULL,                  -- CSV of ISO dates; NULL = whole request
  approver_id VARCHAR(50) NULL,
  acted_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_lcr_request ON leave_cancellation_request(leave_request_id);

ALTER TABLE leave_requests
  ADD COLUMN leave_type_id BIGINT NULL AFTER leave_type,
  ADD COLUMN start_half VARCHAR(10) NULL AFTER end_date,
  ADD COLUMN end_half VARCHAR(10) NULL AFTER start_half,
  ADD COLUMN current_approver_id VARCHAR(50) NULL AFTER approved_by,
  ADD COLUMN attachment_id VARCHAR(64) NULL AFTER rejection_reason,
  ADD COLUMN version INT NOT NULL DEFAULT 0;

-- Backfill pointer from free-text leave_type to leave_type_id where code matches
UPDATE leave_requests r
  JOIN leave_type t ON UPPER(TRIM(r.leave_type)) = t.code
   SET r.leave_type_id = t.id
 WHERE r.leave_type_id IS NULL;
