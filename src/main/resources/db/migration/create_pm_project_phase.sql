CREATE TABLE IF NOT EXISTS pm_project_phases (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    start_date DATE NULL,
    end_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NULL,
    updated_at DATETIME NULL,
    INDEX idx_pm_phase_project (project_id),
    INDEX idx_pm_phase_status (status)
);
