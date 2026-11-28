-- V4 — Sprint 2: access permission matrix + append-only audit log.
-- Does NOT drop role_permissions (legacy PermissionService still reads it until full migration in Sprint 6).

CREATE TABLE access_permission (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    role        VARCHAR(30) NOT NULL,               -- SUPER_ADMIN | HR_MANAGER | HR | EMPLOYEE | PM
    module      VARCHAR(60) NOT NULL,               -- employee, leave, attendance, onboarding, recruitment,
                                                     -- announcement, report, permission, audit, salary, document, dashboard
    action      VARCHAR(20) NOT NULL,               -- VIEW | CREATE | EDIT | DELETE | APPROVE | EXPORT | MANAGE
    allowed     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NULL ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_access_permission UNIQUE (role, module, action),
    CONSTRAINT chk_access_action CHECK (action IN ('VIEW','CREATE','EDIT','DELETE','APPROVE','EXPORT','MANAGE')),
    INDEX idx_access_perm_lookup (role, module, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== Seed: least-privilege baseline =====
-- SUPER_ADMIN: all actions on all modules (enforced by bypass anyway; rows keep the matrix honest)
INSERT INTO access_permission (role, module, action) VALUES
 ('SUPER_ADMIN','employee','MANAGE'),('SUPER_ADMIN','leave','MANAGE'),('SUPER_ADMIN','attendance','MANAGE'),
 ('SUPER_ADMIN','onboarding','MANAGE'),('SUPER_ADMIN','recruitment','MANAGE'),('SUPER_ADMIN','announcement','MANAGE'),
 ('SUPER_ADMIN','report','EXPORT'),('SUPER_ADMIN','permission','MANAGE'),('SUPER_ADMIN','audit','VIEW'),
 ('SUPER_ADMIN','salary','MANAGE'),('SUPER_ADMIN','document','MANAGE'),('SUPER_ADMIN','dashboard','VIEW');

-- HR_MANAGER: full HR operations incl. salary + exports + audit view
INSERT INTO access_permission (role, module, action) VALUES
 ('HR_MANAGER','employee','MANAGE'),('HR_MANAGER','leave','MANAGE'),('HR_MANAGER','attendance','MANAGE'),
 ('HR_MANAGER','onboarding','MANAGE'),('HR_MANAGER','recruitment','MANAGE'),('HR_MANAGER','announcement','MANAGE'),
 ('HR_MANAGER','report','EXPORT'),('HR_MANAGER','audit','VIEW'),('HR_MANAGER','salary','MANAGE'),
 ('HR_MANAGER','document','MANAGE'),('HR_MANAGER','dashboard','VIEW');

-- HR: everything except salary MANAGE + permission MANAGE; salary VIEW allowed (business need), no EDIT/DELETE
INSERT INTO access_permission (role, module, action) VALUES
 ('HR','employee','MANAGE'),('HR','leave','MANAGE'),('HR','attendance','EDIT'),('HR','attendance','VIEW'),
 ('HR','onboarding','MANAGE'),('HR','recruitment','MANAGE'),('HR','announcement','MANAGE'),('HR','report','EXPORT'),
 ('HR','salary','VIEW'),('HR','document','MANAGE'),('HR','dashboard','VIEW');

-- EMPLOYEE: self only at the matrix level; object scope enforced by AccessPolicy
INSERT INTO access_permission (role, module, action) VALUES
 ('EMPLOYEE','employee','VIEW'),('EMPLOYEE','leave','CREATE'),('EMPLOYEE','leave','VIEW'),
 ('EMPLOYEE','attendance','VIEW'),('EMPLOYEE','announcement','VIEW'),('EMPLOYEE','dashboard','VIEW'),
 ('EMPLOYEE','document','VIEW');

-- PM: recruitment pipeline viewing/hiring actions (manage own requisitions)
INSERT INTO access_permission (role, module, action) VALUES
 ('PM','recruitment','VIEW'),('PM','recruitment','EDIT'),('PM','dashboard','VIEW'),('PM','employee','VIEW');

CREATE TABLE audit_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    actor_id    BIGINT NULL,                          -- employees.id of the actor (NULL = anonymous/system)
    actor_role  VARCHAR(30) NULL,
    action      VARCHAR(60) NOT NULL,                 -- HTTP_MUTATION | LOGIN_SUCCESS | LOGIN_FAILURE | FILE_ACCESS | PERMISSION_CHANGED | ...
    entity_type VARCHAR(50) NULL,
    entity_id   VARCHAR(100) NULL,
    old_value   JSON NULL,
    new_value   JSON NULL,
    ip_address  VARCHAR(64) NULL,
    user_agent  VARCHAR(500) NULL,
    trace_id    VARCHAR(64) NULL,
    details     JSON NULL,
    INDEX idx_audit_occurred (occurred_at),
    INDEX idx_audit_actor (actor_id, occurred_at),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_action (action, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Append-only is enforced at the application layer: no UPDATE/DELETE code path exists in this codebase,
-- plus optional DB user without UPDATE/DELETE on audit_log (see 01_SPRINT2_FILE_MANIFEST.md §F).
