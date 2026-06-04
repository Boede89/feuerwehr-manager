-- Testalarme aus dem Testmodus (Startseite); werden beim Beenden des Testmodus gelöscht

CREATE TABLE test_divera_alarms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    alarm_id BIGINT NOT NULL,
    external_id VARCHAR(128) NULL,
    title VARCHAR(512) NOT NULL DEFAULT '',
    alarm_text TEXT NULL,
    address VARCHAR(512) NULL,
    date_epoch_seconds BIGINT NOT NULL DEFAULT 0,
    ts_create_seconds BIGINT NOT NULL DEFAULT 0,
    closed TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    CONSTRAINT fk_test_divera_alarms_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    INDEX idx_test_divera_alarms_unit_open (unit_id, closed, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
