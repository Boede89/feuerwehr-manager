-- Personal-Fahrzeug-Zuordnung im Einsatzbericht

ALTER TABLE incident_report_personnel
    ADD COLUMN incident_report_vehicle_id BIGINT NULL AFTER person_id,
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'MANUAL' AFTER display_name,
    ADD CONSTRAINT fk_ir_personnel_ir_vehicle
        FOREIGN KEY (incident_report_vehicle_id) REFERENCES incident_report_vehicles (id) ON DELETE SET NULL;

CREATE INDEX idx_ir_personnel_vehicle ON incident_report_personnel (incident_report_vehicle_id);
