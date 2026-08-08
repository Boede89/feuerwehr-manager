-- Individuelle Modulrechte (GRANT/DENY) zusätzlich zu Dienstgrad/Funktionen.
CREATE TABLE IF NOT EXISTS user_permission_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission VARCHAR(64) NOT NULL,
    effect VARCHAR(8) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upo_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_upo_user_permission UNIQUE (user_id, permission)
);

SET @idx_exists := (
    SELECT COUNT(1)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'user_permission_overrides'
      AND index_name = 'idx_upo_user'
);
SET @idx_sql := IF(
    @idx_exists = 0,
    'CREATE INDEX idx_upo_user ON user_permission_overrides (user_id)',
    'SELECT 1'
);
PREPARE stmt FROM @idx_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
