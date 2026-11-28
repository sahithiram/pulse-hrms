-- Sprint 6: Notifications (outbox), Announcement acknowledgements, Report support
-- PREREQUISITES: Sprints 0..5. Table names are deliberately prefixed (hrms_*) to avoid
-- colliding with any pre-existing notification/announcement tables discovered in compiled classes.
-- Rename to next free migration number if V8 is taken.

CREATE TABLE hrms_notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(50) NOT NULL,
  event_type VARCHAR(48) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body TEXT NULL,
  deep_link VARCHAR(255) NULL,
  channel VARCHAR(10) NOT NULL DEFAULT 'IN_APP',   -- IN_APP|EMAIL
  status VARCHAR(10) NOT NULL DEFAULT 'UNREAD',     -- UNREAD|READ
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_hrms_notif_user ON hrms_notification(user_id, status, created_at);

CREATE TABLE hrms_notification_preference (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(50) NOT NULL,
  event_type VARCHAR(48) NOT NULL,
  in_app TINYINT(1) NOT NULL DEFAULT 1,
  email TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uq_hnp_user_event UNIQUE (user_id, event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hrms_notification_outbox (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  event_id VARCHAR(64) NOT NULL UNIQUE,        -- dedupe key (business event id, e.g. LV-2026-000123)
  event_type VARCHAR(48) NOT NULL,
  user_id VARCHAR(50) NOT NULL,
  channel VARCHAR(10) NOT NULL DEFAULT 'EMAIL',
  subject VARCHAR(200) NOT NULL,
  body TEXT NULL,
  recipient VARCHAR(255) NULL,                  -- explicit email override (document-deadline job); falls back to userId mapping
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING', -- PENDING|SENT|FAILED
  attempts INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_error VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_hno_due ON hrms_notification_outbox(status, next_retry_at);

-- ===== Existing announcements table (VERIFIED from source: config/AnnouncementSchemaInitializer.java) =====
-- Created IF NOT EXISTS here (identical DDL) so this migration is ordering-safe on fresh installs:
-- Flyway (V8) runs before the @PostConstruct initializer; with this statement whichever runs first wins.
CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  type ENUM('PERMANENT','TEMPORARY') NOT NULL,
  announcement_type VARCHAR(20) NOT NULL,
  expiry_date DATETIME NULL,
  category VARCHAR(100),
  priority ENUM('LOW','NORMAL','HIGH','URGENT') NOT NULL DEFAULT 'NORMAL',
  target_audience ENUM('ALL','HR_ONLY','EMPLOYEES_ONLY') NOT NULL DEFAULT 'ALL',
  created_by VARCHAR(100),
  created_by_name VARCHAR(255),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at DATETIME NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  is_pinned BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sprint 6 scheduling + ack-required policy live in a SIDE TABLE (zero ALTER on legacy table).
CREATE TABLE announcements_policy (
  announcement_id BIGINT PRIMARY KEY,
  requires_ack TINYINT(1) NOT NULL DEFAULT 0,
  publish_at DATETIME NULL,
  CONSTRAINT fk_ap_ann FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Department/location/role/individual targeting (legacy is ALL/HR_ONLY/EMPLOYEES_ONLY only).
CREATE TABLE announcement_target (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  announcement_id BIGINT NOT NULL,
  target_type VARCHAR(16) NOT NULL,          -- DEPARTMENT|LOCATION|ROLE|INDIVIDUAL
  target_value VARCHAR(128) NOT NULL,
  CONSTRAINT uq_at UNIQUE (announcement_id, target_type, target_value),
  INDEX idx_at_value (target_type, target_value),
  CONSTRAINT fk_at_ann FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE announcement_ack (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  announcement_id BIGINT NOT NULL,
  user_id VARCHAR(50) NOT NULL,
  acked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_ack_ann_user UNIQUE (announcement_id, user_id),
  CONSTRAINT fk_ack_ann FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Saved report views (REQ-6-11): named filter presets per user per report.
CREATE TABLE hrms_report_saved_view (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id VARCHAR(50) NOT NULL,
  report_key VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  filters_json TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_hrsv UNIQUE (user_id, report_key, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
