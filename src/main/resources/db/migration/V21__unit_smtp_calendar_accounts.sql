-- Mehrere SMTP- und Kalender-Zugänge pro Einheit (mit Bezeichnung)

CREATE TABLE unit_smtp_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    label VARCHAR(128) NOT NULL DEFAULT '',
    smtp_host VARCHAR(255) NULL,
    smtp_port INT NULL,
    smtp_username VARCHAR(255) NULL,
    smtp_password VARCHAR(512) NULL,
    smtp_from_email VARCHAR(255) NULL,
    smtp_from_name VARCHAR(255) NULL,
    smtp_encryption VARCHAR(16) NULL DEFAULT 'TLS',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_smtp_accounts_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    INDEX idx_unit_smtp_accounts_unit (unit_id, sort_order, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE unit_calendar_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    label VARCHAR(128) NOT NULL DEFAULT '',
    provider VARCHAR(32) NOT NULL DEFAULT 'google',
    calendar_url VARCHAR(1024) NULL,
    calendar_id VARCHAR(512) NULL,
    service_account_json MEDIUMTEXT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_calendar_accounts_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    INDEX idx_unit_calendar_accounts_unit (unit_id, sort_order, label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO unit_smtp_accounts (
    unit_id, label, smtp_host, smtp_port, smtp_username, smtp_password,
    smtp_from_email, smtp_from_name, smtp_encryption)
SELECT
    unit_id,
    'Standard',
    smtp_host,
    smtp_port,
    smtp_username,
    smtp_password,
    smtp_from_email,
    smtp_from_name,
    COALESCE(smtp_encryption, 'TLS')
FROM unit_smtp_settings
WHERE COALESCE(TRIM(smtp_host), '') != ''
   OR COALESCE(TRIM(smtp_from_email), '') != ''
   OR COALESCE(TRIM(smtp_password), '') != '';

INSERT INTO unit_calendar_accounts (
    unit_id, label, provider, calendar_url, calendar_id, service_account_json, enabled)
SELECT
    unit_id,
    'Standard',
    COALESCE(provider, 'google'),
    calendar_url,
    calendar_id,
    service_account_json,
    enabled
FROM unit_calendar_settings
WHERE COALESCE(TRIM(calendar_url), '') != ''
   OR COALESCE(TRIM(calendar_id), '') != ''
   OR COALESCE(TRIM(service_account_json), '') != ''
   OR enabled = 1;

DROP TABLE unit_smtp_settings;
DROP TABLE unit_calendar_settings;
