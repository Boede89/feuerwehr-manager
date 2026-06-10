CREATE TABLE incident_report_attachments (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    incident_report_id   BIGINT       NOT NULL,
    filename             VARCHAR(512) NOT NULL,
    stored_name          VARCHAR(255) NOT NULL,
    mime_type            VARCHAR(128) NOT NULL,
    file_size            BIGINT       NOT NULL,
    uploaded_by_user_id  BIGINT       NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inc_attach_report FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_inc_attach_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_inc_attach_report ON incident_report_attachments (incident_report_id);
