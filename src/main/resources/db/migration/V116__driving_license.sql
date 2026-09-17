CREATE TABLE unit_driving_license_settings (
    unit_id BIGINT NOT NULL PRIMARY KEY,
    interval_months INT NOT NULL DEFAULT 12,
    warn_days INT NOT NULL DEFAULT 30,
    notify_person TINYINT(1) NOT NULL DEFAULT 0,
    notification_user_ids TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_driving_license_settings_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE TABLE driving_licenses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    person_id BIGINT NOT NULL,
    presence VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    classes_csv VARCHAR(128),
    issued_on DATE,
    number_suffix VARCHAR(16),
    restrictions TEXT,
    self_report_acknowledged TINYINT(1) NOT NULL DEFAULT 0,
    next_due_on DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_driving_license_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT uq_driving_license_person UNIQUE (person_id)
);

CREATE INDEX idx_driving_licenses_next_due ON driving_licenses (next_due_on);

CREATE TABLE driving_license_checks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_id BIGINT NOT NULL,
    checked_on DATE NOT NULL,
    next_due_on DATE NOT NULL,
    result VARCHAR(16) NOT NULL,
    classes_csv VARCHAR(128),
    notes VARCHAR(1024),
    checked_by_user_id BIGINT,
    checked_by_display_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_driving_license_check_license FOREIGN KEY (license_id) REFERENCES driving_licenses (id) ON DELETE CASCADE,
    CONSTRAINT fk_driving_license_check_user FOREIGN KEY (checked_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_driving_license_checks_license ON driving_license_checks (license_id);
CREATE INDEX idx_driving_license_checks_checked_on ON driving_license_checks (checked_on);

CREATE TABLE driving_license_reminder_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    license_id BIGINT NOT NULL,
    mail_kind VARCHAR(16) NOT NULL,
    next_due_on DATE NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    person_notified TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_driving_license_reminder_license FOREIGN KEY (license_id) REFERENCES driving_licenses (id) ON DELETE CASCADE,
    CONSTRAINT uq_driving_license_reminder UNIQUE (license_id, mail_kind, next_due_on)
);

CREATE INDEX idx_driving_license_reminder_due ON driving_license_reminder_log (next_due_on);
