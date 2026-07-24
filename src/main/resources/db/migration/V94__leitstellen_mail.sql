CREATE TABLE unit_leitstellen_mail_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    imap_host VARCHAR(255) NULL,
    imap_port INT NULL,
    imap_username VARCHAR(255) NULL,
    imap_password VARCHAR(512) NULL,
    imap_encryption VARCHAR(16) NOT NULL DEFAULT 'SSL',
    imap_folder VARCHAR(128) NOT NULL DEFAULT 'INBOX',
    from_filter VARCHAR(255) NULL,
    subject_filter VARCHAR(255) NULL,
    depesche_keywords VARCHAR(512) NOT NULL DEFAULT 'depesche,alarmdepesche,alarm',
    abschluss_keywords VARCHAR(512) NOT NULL DEFAULT 'abschluss,abschlussbericht,endebericht',
    poll_lookback_hours INT NOT NULL DEFAULT 24,
    match_window_hours INT NOT NULL DEFAULT 12,
    last_poll_at TIMESTAMP NULL,
    last_poll_message VARCHAR(512) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_leitstellen_mail_unit UNIQUE (unit_id),
    CONSTRAINT fk_leitstellen_mail_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE TABLE leitstellen_mail_imports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    incident_report_id BIGINT NULL,
    message_id VARCHAR(512) NOT NULL,
    imap_uid BIGINT NULL,
    attachment_name VARCHAR(255) NOT NULL,
    attachment_sha256 CHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_leitstellen_mail_import UNIQUE (unit_id, attachment_sha256),
    CONSTRAINT fk_leitstellen_import_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_leitstellen_import_report FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE SET NULL
);

CREATE INDEX idx_leitstellen_import_message ON leitstellen_mail_imports (unit_id, message_id(191));
