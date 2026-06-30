-- Einsatz-App: Push-Einstellungen pro Einheit, Geräte-Token, Versand-Protokoll

CREATE TABLE unit_einsatzapp_settings (
    unit_id    BIGINT    NOT NULL PRIMARY KEY,
    push_enabled BOOLEAN   NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ues_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE einsatzapp_device_tokens (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    unit_id      BIGINT       NOT NULL,
    fcm_token    VARCHAR(512) NOT NULL,
    device_label VARCHAR(128) NULL,
    platform     VARCHAR(32)  NOT NULL DEFAULT 'android',
    last_seen_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_edt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_edt_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_edt_user_token UNIQUE (user_id, fcm_token),
    INDEX idx_edt_unit (unit_id),
    INDEX idx_edt_token (fcm_token(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE einsatzapp_push_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id         BIGINT       NOT NULL,
    divera_alarm_id BIGINT       NULL,
    alarm_title     VARCHAR(512) NULL,
    tokens_targeted INT          NOT NULL DEFAULT 0,
    tokens_sent     INT          NOT NULL DEFAULT 0,
    error_message   TEXT         NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_epl_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    INDEX idx_epl_unit_created (unit_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
