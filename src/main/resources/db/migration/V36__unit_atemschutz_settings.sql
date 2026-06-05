CREATE TABLE unit_atemschutz_settings (
    unit_id BIGINT NOT NULL PRIMARY KEY,
    warn_days INT NOT NULL DEFAULT 90,
    agt_course_name VARCHAR(64) NOT NULL DEFAULT 'AGT',
    notification_user_ids TEXT,
    cc_user_ids TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_atemschutz_settings_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE TABLE atemschutz_email_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    template_key VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_atemschutz_email_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_atemschutz_email_unit_key UNIQUE (unit_id, template_key)
);
