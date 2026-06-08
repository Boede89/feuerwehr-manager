-- Einsatzbericht: Stichwort, Objekt, Eigentümer, Einsatzleiter-Person, Kräfte & Fahrzeuge

ALTER TABLE incident_reports
    ADD COLUMN stichwort VARCHAR(255) NULL AFTER incident_type_label,
    ADD COLUMN objekt VARCHAR(255) NULL AFTER house_number,
    ADD COLUMN eigentuemer VARCHAR(255) NULL AFTER objekt,
    ADD COLUMN commander_person_id BIGINT NULL AFTER incident_commander,
    ADD CONSTRAINT fk_incident_report_commander_person
        FOREIGN KEY (commander_person_id) REFERENCES persons (id) ON DELETE SET NULL;

UPDATE incident_reports
SET stichwort = incident_type_label
WHERE stichwort IS NULL OR TRIM(stichwort) = '';

CREATE TABLE incident_report_personnel (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_report_id BIGINT       NOT NULL,
    person_id          BIGINT       NULL,
    display_name       VARCHAR(255) NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ir_personnel_report FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_ir_personnel_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ir_personnel_report ON incident_report_personnel (incident_report_id);

CREATE TABLE incident_report_vehicles (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_report_id BIGINT       NOT NULL,
    vehicle_id         BIGINT       NULL,
    vehicle_name       VARCHAR(100) NOT NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ir_vehicle_report FOREIGN KEY (incident_report_id) REFERENCES incident_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_ir_vehicle_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ir_vehicle_report ON incident_report_vehicles (incident_report_id);
