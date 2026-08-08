CREATE TABLE user_permission_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission VARCHAR(64) NOT NULL,
    effect VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upo_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_upo_user_permission UNIQUE (user_id, permission),
    CONSTRAINT chk_upo_effect CHECK (effect IN ('GRANT', 'DENY'))
);

CREATE INDEX idx_upo_user ON user_permission_overrides (user_id);
