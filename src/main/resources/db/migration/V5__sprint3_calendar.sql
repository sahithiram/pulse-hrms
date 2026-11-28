-- Sprint 3: Calendar, Holidays, Weekly Off, Working-Day Service
-- PREREQUISITES: Sprint 0 (baseline), Sprint 1 (locations, employee org ids), Sprint 2 (security)
-- If your project already has a V5__*.sql, rename this file to the next free version number.

CREATE TABLE work_calendar (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  location_id BIGINT NULL,
  is_default TINYINT(1) NOT NULL DEFAULT 0,
  valid_from DATE NULL,
  valid_to DATE NULL,
  created_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_work_calendar_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_work_calendar_location ON work_calendar(location_id);
CREATE INDEX idx_work_calendar_default ON work_calendar(is_default);

CREATE TABLE weekly_off_rule (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  calendar_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL,             -- ISO: 1=MON .. 7=SUN
  pattern VARCHAR(10) NOT NULL DEFAULT 'ALL', -- ALL|ODD|EVEN|FIRST|SECOND|THIRD|FOURTH|FIFTH|LAST
  CONSTRAINT fk_wor_calendar FOREIGN KEY (calendar_id) REFERENCES work_calendar(id) ON DELETE CASCADE,
  CONSTRAINT uq_wor_calendar_day_pattern UNIQUE (calendar_id, day_of_week, pattern)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE holidays
  ADD COLUMN calendar_id BIGINT NULL AFTER id,
  ADD COLUMN is_half_day TINYINT(1) NOT NULL DEFAULT 0 AFTER holiday_type;
CREATE INDEX idx_holiday_calendar ON holidays(calendar_id, holiday_date);
-- One holiday per (calendar, date, name); MySQL treats NULLs as distinct so legacy rows keep working
ALTER TABLE holidays ADD CONSTRAINT uq_holiday_cal_date_name UNIQUE (calendar_id, holiday_date, holiday_name);

CREATE TABLE optional_holiday_quota (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  calendar_id BIGINT NOT NULL,
  year INT NOT NULL,
  max_optional_per_employee INT NOT NULL DEFAULT 2,
  CONSTRAINT fk_ohq_calendar FOREIGN KEY (calendar_id) REFERENCES work_calendar(id) ON DELETE CASCADE,
  CONSTRAINT uq_ohq_calendar_year UNIQUE (calendar_id, year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE optional_holiday_choice (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  employee_id VARCHAR(32) NOT NULL,
  holiday_id BIGINT NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'CHOSEN', -- CHOSEN|REJECTED
  chosen_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_ohc_holiday FOREIGN KEY (holiday_id) REFERENCES holidays(id) ON DELETE CASCADE,
  CONSTRAINT uq_ohc_employee_holiday UNIQUE (employee_id, holiday_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_ohc_employee ON optional_holiday_choice(employee_id);

CREATE TABLE working_day_override (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  calendar_id BIGINT NOT NULL,
  override_date DATE NOT NULL,
  override_type VARCHAR(10) NOT NULL,        -- WORKING|OFF
  reason VARCHAR(255) NULL,
  CONSTRAINT fk_wdo_calendar FOREIGN KEY (calendar_id) REFERENCES work_calendar(id) ON DELETE CASCADE,
  CONSTRAINT uq_wdo_calendar_date UNIQUE (calendar_id, override_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed one default calendar with Sunday weekly-off (idempotent)
INSERT INTO work_calendar (name, location_id, is_default, valid_from)
SELECT 'Default Corporate Calendar', NULL, 1, '2024-01-01'
WHERE NOT EXISTS (SELECT 1 FROM work_calendar WHERE is_default = 1);

INSERT INTO weekly_off_rule (calendar_id, day_of_week, pattern)
SELECT c.id, 7, 'ALL' FROM work_calendar c
WHERE c.is_default = 1
  AND NOT EXISTS (SELECT 1 FROM weekly_off_rule w WHERE w.calendar_id = c.id AND w.day_of_week = 7 AND w.pattern = 'ALL');

-- Point all existing holidays at the default calendar
UPDATE holidays
SET calendar_id = (SELECT id FROM (SELECT id FROM work_calendar WHERE is_default = 1 LIMIT 1) d)
WHERE calendar_id IS NULL;
