-- Gerätewartmitteilungen

CREATE TABLE equipment_maintenance_reports (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id              BIGINT       NOT NULL,
    typ                  VARCHAR(16)  NOT NULL,
    event_date           DATE         NOT NULL,
    readiness            VARCHAR(32)  NOT NULL DEFAULT 'HERGESTELLT',
    leader_person_id     BIGINT       NULL,
    leader_name          VARCHAR(255) NULL,
    deployed_equipment_json TEXT      NULL,
    created_by_user_id   BIGINT       NULL,
    created_by_name      VARCHAR(255) NULL,
    test_data            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_eq_maint_report_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_eq_maint_report_leader FOREIGN KEY (leader_person_id) REFERENCES persons (id) ON DELETE SET NULL,
    CONSTRAINT fk_eq_maint_report_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_eq_maint_reports_unit_date ON equipment_maintenance_reports (unit_id, event_date DESC);
