CREATE TABLE IF NOT EXISTS user_accounts (
    user_id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    user_role VARCHAR(16) NOT NULL CHECK (user_role IN ('STUDENT', 'ADMIN')),
    account_status VARCHAR(16) NOT NULL CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    profile_data JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS courses (
    course_code VARCHAR(32) PRIMARY KEY,
    course_name VARCHAR(200) NOT NULL
);
