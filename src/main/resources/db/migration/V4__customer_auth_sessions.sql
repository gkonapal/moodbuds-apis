ALTER TABLE users
    ADD COLUMN failed_login_attempts TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER is_active,
    ADD COLUMN locked_until DATETIME NULL AFTER failed_login_attempts;

CREATE TABLE customer_auth_sessions (
    id CHAR(36) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_customer_auth_sessions_refresh_hash (refresh_token_hash),
    KEY idx_customer_auth_sessions_user (user_id),
    KEY idx_customer_auth_sessions_expiry (expires_at),
    CONSTRAINT fk_customer_auth_sessions_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
