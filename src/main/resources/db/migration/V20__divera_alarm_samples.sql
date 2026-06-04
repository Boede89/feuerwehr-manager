-- Im Testmodus aus DIVERA übernommene Einsätze als wiederverwendbare Webhook-Beispiele

CREATE TABLE divera_alarm_samples (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    alarm_id BIGINT NOT NULL,
    title VARCHAR(512) NOT NULL DEFAULT '',
    address VARCHAR(512) NULL,
    webhook_payload TEXT NOT NULL,
    captured_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_divera_alarm_samples_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    UNIQUE KEY uk_divera_alarm_samples_unit_alarm (unit_id, alarm_id),
    INDEX idx_divera_alarm_samples_unit_captured (unit_id, captured_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
