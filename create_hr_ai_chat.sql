CREATE TABLE IF NOT EXISTS hr_ai_chat_sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_id VARCHAR(100) NOT NULL,      -- HR user's employeeId (session.getAttribute("userId"))
    session_name VARCHAR(255),
    message_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS hr_ai_chat_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,              -- 'user' | 'assistant'
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hr_ai_msg_session (session_id)
);
