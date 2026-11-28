-- Sprint 5: Attendance, Shifts, Regularization, Daily Metrics
-- PREREQUISITES: Sprints 0..4 applied. Rename if V7 is taken.

CREATE TABLE shift (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(60) NOT NULL UNIQUE,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  crosses_midnight TINYINT(1) NOT NULL DEFAULT 0,
  grace_minutes INT NOT NULL DEFAULT 0,
  full_day_minutes INT NOT NULL DEFAULT 480,
  half_day_minutes INT NOT NULL DEFAULT 240,
  min_break_minutes INT NOT NULL DEFAULT 0,
  overtime_after_minutes INT NOT NULL DEFAULT 0,
  is_flexible TINYINT(1) NOT NULL DEFAULT 0,
  core_hours_start TIME NULL,
  core_hours_end TIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO shift (name, start_time, end_time, crosses_midnight, grace_minutes, full_day_minutes, half_day_minutes, is_flexible, core_hours_start, core_hours_end)
SELECT * FROM (
  SELECT 'General',  '09:00:00', '18:00:00', 0, 15, 480, 240, 0, NULL, NULL UNION ALL
  SELECT 'Morning',  '07:00:00', '16:00:00', 0, 15, 480, 240, 0, NULL, NULL UNION ALL
  SELECT 'Night',    '21:00:00', '06:00:00', 1, 15, 480, 240, 0, NULL, NULL UNION ALL
  SELECT 'Flexible', '09:00:00', '18:00:00', 0, 60, 480, 240, 1, '11:00:00', '16:00:00'
) s(name, st, et, cm, g, fd, hd, flex, cs, ce)
WHERE NOT EXISTS (SELECT 1 FROM shift);

CREATE TABLE employee_shift (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  shift_id BIGINT NOT NULL,
  effective_from DATE NOT NULL,
  effective_to DATE NULL,
  CONSTRAINT fk_es_shift FOREIGN KEY (shift_id) REFERENCES shift(id),
  CONSTRAINT uq_es_emp_from UNIQUE (employee_id, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_es_emp ON employee_shift(employee_id, effective_from);

CREATE TABLE roster_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  roster_date DATE NOT NULL,
  shift_id BIGINT NOT NULL,
  CONSTRAINT fk_re_shift FOREIGN KEY (shift_id) REFERENCES shift(id),
  CONSTRAINT uq_re_emp_date UNIQUE (employee_id, roster_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attendance_regularization (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(50) NOT NULL,
  attendance_date DATE NOT NULL,
  type VARCHAR(20) NOT NULL,             -- MISSED_CHECKIN|MISSED_CHECKOUT|WRONG_TIME|WORK_FROM_HOME|ON_DUTY
  requested_in DATETIME NULL,
  requested_out DATETIME NULL,
  reason VARCHAR(500) NULL,
  attachment VARCHAR(64) NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
  approver VARCHAR(50) NULL,
  acted_at DATETIME NULL,
  version INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_reg_emp_month ON attendance_regularization(employee_id, attendance_date);
CREATE INDEX idx_reg_status ON attendance_regularization(status);

CREATE TABLE employee_daily_metrics (
  employee_id VARCHAR(50) NOT NULL,
  metrics_date DATE NOT NULL,
  day_type VARCHAR(24) NOT NULL,
  shift_id BIGINT NULL,
  status VARCHAR(16) NOT NULL,           -- PRESENT|HALF_DAY|ABSENT|ON_LEAVE|HOLIDAY|WEEKLY_OFF|NOT_EMPLOYED|INCOMPLETE
  first_in DATETIME NULL,
  last_out DATETIME NULL,
  gross_minutes INT NOT NULL DEFAULT 0,
  break_minutes INT NOT NULL DEFAULT 0,
  net_minutes INT NOT NULL DEFAULT 0,
  expected_minutes INT NOT NULL DEFAULT 480,
  late_minutes INT NOT NULL DEFAULT 0,
  early_out_minutes INT NOT NULL DEFAULT 0,
  overtime_minutes INT NOT NULL DEFAULT 0,
  leave_fraction DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  is_regularized TINYINT(1) NOT NULL DEFAULT 0,
  computed_at DATETIME NOT NULL,
  rule_version INT NOT NULL DEFAULT 1,
  PRIMARY KEY (employee_id, metrics_date),
  CONSTRAINT fk_edm_shift FOREIGN KEY (shift_id) REFERENCES shift(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_edm_date ON employee_daily_metrics(metrics_date);

ALTER TABLE attendance
  ADD COLUMN source VARCHAR(12) NOT NULL DEFAULT 'WEB' AFTER status,
  ADD COLUMN ip_address VARCHAR(64) NULL AFTER source,
  ADD COLUMN latitude DECIMAL(10,7) NULL AFTER ip_address,
  ADD COLUMN longitude DECIMAL(10,7) NULL AFTER latitude,
  ADD COLUMN work_mode VARCHAR(16) NULL AFTER longitude,
  ADD COLUMN is_auto_closed TINYINT(1) NOT NULL DEFAULT 0 AFTER work_mode,
  ADD COLUMN edited_flag TINYINT(1) NOT NULL DEFAULT 0 AFTER is_auto_closed;
-- NOTE: apply the unique key only after de-duplicating existing rows:
-- ALTER TABLE attendance ADD CONSTRAINT uq_att_emp_date_session UNIQUE (employee_id, attendance_date, session_number);
