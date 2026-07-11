CREATE TABLE unit_berichte_email_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    report_type VARCHAR(32) NOT NULL,
    email_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    send_on_status VARCHAR(16) NULL,
    person_ids_json TEXT NOT NULL,
    manual_emails_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_berichte_email_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT uq_berichte_email_unit_type UNIQUE (unit_id, report_type)
);
