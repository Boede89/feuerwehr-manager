-- Abruf-Intervalle und Poll-Sessions (DIVERA-gesteuert)
ALTER TABLE unit_leitstellen_mail_settings
    ADD COLUMN depesche_poll_interval_seconds INT NOT NULL DEFAULT 60 AFTER match_window_hours,
    ADD COLUMN abschluss_poll_interval_seconds INT NOT NULL DEFAULT 300 AFTER depesche_poll_interval_seconds;

CREATE TABLE leitstellen_mail_poll_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    incident_report_id BIGINT NOT NULL,
    phase VARCHAR(32) NOT NULL,
    next_poll_at TIMESTAMP NOT NULL,
    last_poll_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT uq_leitstellen_poll_report UNIQUE (incident_report_id),
    CONSTRAINT fk_leitstellen_poll_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_leitstellen_poll_report FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE CASCADE
);

CREATE INDEX idx_leitstellen_poll_due ON leitstellen_mail_poll_sessions (phase, next_poll_at);
CREATE INDEX idx_leitstellen_poll_unit ON leitstellen_mail_poll_sessions (unit_id, phase);
