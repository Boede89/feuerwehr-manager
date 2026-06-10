-- Änderungshistorie für Einsatzberichte (erweitert gegenüber FW-Hub mit Feld-Diffs)

CREATE TABLE incident_report_changes (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_report_id  BIGINT       NOT NULL,
    changed_by_user_id  BIGINT       NULL,
    changed_by_name     VARCHAR(255) NULL,
    comment_text        TEXT         NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ir_change_report
        FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_ir_change_user
        FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ir_changes_report ON incident_report_changes (incident_report_id);
CREATE INDEX idx_ir_changes_created ON incident_report_changes (created_at DESC);

CREATE TABLE incident_report_change_fields (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    change_id    BIGINT       NOT NULL,
    field_key    VARCHAR(64)  NOT NULL,
    field_label  VARCHAR(128) NOT NULL,
    old_value    TEXT         NULL,
    new_value    TEXT         NULL,
    CONSTRAINT fk_ir_change_field_change
        FOREIGN KEY (change_id) REFERENCES incident_report_changes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ir_change_fields_change ON incident_report_change_fields (change_id);
