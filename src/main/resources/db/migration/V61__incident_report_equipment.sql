-- Im Einsatz verwendete Fahrzeuggeräte

CREATE TABLE incident_report_equipment (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_report_id   BIGINT       NOT NULL,
    vehicle_id           BIGINT       NOT NULL,
    vehicle_equipment_id BIGINT       NULL,
    equipment_name       VARCHAR(255) NOT NULL,
    category_name        VARCHAR(255) NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ire_report FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_ire_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE CASCADE,
    CONSTRAINT fk_ire_equipment FOREIGN KEY (vehicle_equipment_id) REFERENCES vehicle_equipment (id) ON DELETE SET NULL
);

CREATE INDEX idx_ire_report ON incident_report_equipment (incident_report_id);
