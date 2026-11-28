-- Sprint 7: Recruitment depth — requisitions, pipeline history, interviews, offer workflow, hand-off support
-- PREREQUISITES: Sprints 0..6. Relies on verified tables: jobs, job_applications, offer_letters (id, no FK — table name unverified),
-- employees (V3 columns employment_status/employment_status_id etc.). FK to legacy tables only where type is verified (job_applications.id BIGINT AI).
-- Legacy tables are NOT altered anywhere in this migration (zero drift).
-- Rename migration number if V9 is taken.

CREATE TABLE requisition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  req_code VARCHAR(24) NOT NULL UNIQUE,          -- RQ-YYYY-NNNNNN from hrms_seq('REQUISITION')
  title VARCHAR(200) NOT NULL,
  department VARCHAR(100) NULL,
  location VARCHAR(128) NULL,
  headcount INT NOT NULL DEFAULT 1,
  budget_min DECIMAL(15,2) NULL,
  budget_max DECIMAL(15,2) NULL,
  currency CHAR(3) NULL,
  hiring_manager VARCHAR(50) NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',   -- DRAFT|PENDING_APPROVAL|OPEN|ON_HOLD|CLOSED
  closed_reason VARCHAR(255) NULL,
  created_by VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_by VARCHAR(50) NULL,
  approved_at DATETIME NULL,
  CONSTRAINT chk_req_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','OPEN','ON_HOLD','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE job_requisition_link (           -- job_id PK; no FK to jobs (legacy ownership unverified) — application-enforced
  job_id BIGINT PRIMARY KEY,
  requisition_id BIGINT NOT NULL,
  linked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_jrl_req FOREIGN KEY (requisition_id) REFERENCES requisition(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_stage_history (       -- append-only audit of stage movement (REQ-7-02)
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  application_id BIGINT NOT NULL,
  from_stage VARCHAR(24) NULL,
  to_stage VARCHAR(24) NOT NULL,
  changed_by VARCHAR(50) NULL,
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reason VARCHAR(255) NULL,
  CONSTRAINT fk_ash_app FOREIGN KEY (application_id) REFERENCES job_applications(id) ON DELETE CASCADE,
  INDEX idx_ash_app (application_id, changed_at),
  INDEX idx_ash_stage (to_stage, changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_source (              -- REQ-7-05 source tracking
  application_id BIGINT PRIMARY KEY,
  source VARCHAR(24) NOT NULL,                 -- PORTAL|REFERRAL|AGENCY|CAMPUS|OTHER
  source_detail VARCHAR(128) NULL,
  CONSTRAINT fk_asrc_app FOREIGN KEY (application_id) REFERENCES job_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE application_meta (                -- duplicate-detection support; populated by intake flow (see 07_EVENT_WIRING)
  application_id BIGINT PRIMARY KEY,
  resume_hash CHAR(64) NULL,
  phone_normalized VARCHAR(20) NULL,           -- digits only, last 10
  talent_pool TINYINT(1) NOT NULL DEFAULT 0,
  rejection_reason VARCHAR(255) NULL,
  CONSTRAINT fk_ameta_app FOREIGN KEY (application_id) REFERENCES job_applications(id) ON DELETE CASCADE,
  INDEX idx_ameta_hash (resume_hash),
  INDEX idx_ameta_phone (phone_normalized)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE candidate_consent (
  application_id BIGINT PRIMARY KEY,
  consented_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  retention_until DATE NULL,
  CONSTRAINT fk_cc_app FOREIGN KEY (application_id) REFERENCES job_applications(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interview (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  application_id BIGINT NOT NULL,
  round INT NOT NULL DEFAULT 1,
  interviewers VARCHAR(500) NOT NULL,          -- comma-separated employee ids (documented decision; simple)
  scheduled_at DATETIME NOT NULL,
  mode VARCHAR(10) NOT NULL DEFAULT 'VIDEO',   -- ONSITE|VIDEO|PHONE
  link_location VARCHAR(255) NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED|COMPLETED|CANCELLED|NO_SHOW
  CONSTRAINT fk_interview_app FOREIGN KEY (application_id) REFERENCES job_applications(id) ON DELETE CASCADE,
  INDEX idx_interview_app (application_id),
  INDEX idx_interview_when (scheduled_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE interview_feedback (              -- hidden from other interviewers until they submit (REQ-7-03)
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  interview_id BIGINT NOT NULL,
  interviewer_id VARCHAR(50) NOT NULL,
  competency_ratings TEXT NULL,                -- JSON, e.g. {"communication":4,"ownership":5}
  recommendation VARCHAR(12) NOT NULL,         -- STRONG_YES|YES|NO|STRONG_NO
  notes TEXT NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_feedback UNIQUE (interview_id, interviewer_id),
  CONSTRAINT fk_feedback_interview FOREIGN KEY (interview_id) REFERENCES interview(id) ON DELETE CASCADE,
  CONSTRAINT chk_fb_rec CHECK (recommendation IN ('STRONG_YES','YES','NO','STRONG_NO'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE offer_workflow (                  -- augments legacy offer_letters (DRAFT/GENERATED/SENT); never alters it
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  offer_letter_id BIGINT NOT NULL UNIQUE,      -- NO FK: legacy offer table name not verified (OfferLetter.java not read)
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT', -- DRAFT|PENDING_APPROVAL|APPROVED|SENT|ACCEPTED|DECLINED|EXPIRED|WITHDRAWN|REVISED
  version INT NOT NULL DEFAULT 1,
  valid_until DATE NULL,
  approver_hr VARCHAR(50) NULL,
  approver_budget VARCHAR(50) NULL,
  approved_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL,
  CONSTRAINT chk_owf_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','SENT','ACCEPTED','DECLINED','EXPIRED','WITHDRAWN','REVISED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE offer_revision (                  -- version history of offer content (REQ-7-04)
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  offer_letter_id BIGINT NOT NULL,
  version INT NOT NULL,
  designation VARCHAR(120) NULL,
  department VARCHAR(100) NULL,
  annual_salary VARCHAR(64) NULL,
  currency CHAR(3) NULL,
  joining_date VARCHAR(32) NULL,
  employment_type VARCHAR(32) NULL,
  change_reason VARCHAR(255) NOT NULL,
  revised_by VARCHAR(50) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uq_rev (offer_letter_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hrms_seq (                        -- tiny sequence table (REQUISITION, EMPLOYEE, ...)
  name VARCHAR(32) PRIMARY KEY,
  next_val BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO hrms_seq(name, next_val) VALUES ('REQUISITION', 1), ('EMPLOYEE', 1);
