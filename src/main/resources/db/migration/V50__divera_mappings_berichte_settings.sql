-- Divera: Empfänger-Gruppen und Status-IDs pro Einheit

CREATE TABLE unit_divera_recipient_groups (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id    BIGINT       NOT NULL,
    group_id   VARCHAR(64)  NOT NULL,
    label      VARCHAR(128) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_udrg_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_udrg_unit_group UNIQUE (unit_id, group_id),
    INDEX idx_udrg_unit (unit_id, sort_order, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE unit_divera_status_ids (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    unit_id    BIGINT       NOT NULL,
    status_id  VARCHAR(64)  NOT NULL,
    label      VARCHAR(128) NOT NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_udsi_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_udsi_unit_status UNIQUE (unit_id, status_id),
    INDEX idx_udsi_unit (unit_id, sort_order, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Berichte-Modul: Einstellungen pro Einheit

CREATE TABLE unit_berichte_settings (
    unit_id                          BIGINT    NOT NULL PRIMARY KEY,
    import_incident_data_from_divera BOOLEAN   NOT NULL DEFAULT FALSE,
    import_personnel_from_divera     BOOLEAN   NOT NULL DEFAULT FALSE,
    einsatz_personnel_status_ids     TEXT      NULL,
    updated_at                       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_ubs_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
