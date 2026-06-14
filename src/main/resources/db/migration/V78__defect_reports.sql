-- Mängelberichte

CREATE TABLE defect_reports (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id               BIGINT       NOT NULL,
    standort              VARCHAR(100) NOT NULL,
    mangel_an             VARCHAR(50)  NOT NULL,
    bezeichnung           VARCHAR(255) NULL,
    mangel_beschreibung   TEXT         NULL,
    ursache               TEXT         NULL,
    verbleib              TEXT         NULL,
    recorded_person_id    BIGINT       NULL,
    recorded_by_text      VARCHAR(255) NULL,
    aufgenommen_am        DATE         NOT NULL,
    vehicle_id            BIGINT       NULL,
    created_by_user_id    BIGINT       NULL,
    created_by_name       VARCHAR(255) NULL,
    test_data             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_defect_report_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE,
    CONSTRAINT fk_defect_report_person FOREIGN KEY (recorded_person_id) REFERENCES persons (id) ON DELETE SET NULL,
    CONSTRAINT fk_defect_report_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE SET NULL,
    CONSTRAINT fk_defect_report_created_by FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_defect_reports_unit_date ON defect_reports (unit_id, aufgenommen_am DESC);
