CREATE TABLE units (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unit_divera_settings (
    unit_id BIGINT NOT NULL,
    api_base_url VARCHAR(512) NOT NULL DEFAULT 'https://app.divera247.com',
    access_key VARCHAR(2048) NOT NULL,
    PRIMARY KEY (unit_id),
    CONSTRAINT fk_uds_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO units (id, name, active) VALUES (1, 'Demo-Einheit', TRUE);
INSERT INTO unit_divera_settings (unit_id, api_base_url, access_key)
VALUES (1, 'https://app.divera247.com', '');
