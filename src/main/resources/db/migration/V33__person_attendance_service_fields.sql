ALTER TABLE person_attendance
    ADD COLUMN service_label VARCHAR(64) NULL,
    ADD COLUMN service_type VARCHAR(32) NOT NULL DEFAULT 'UEBUNGSDIENST';

CREATE INDEX idx_person_attendance_type ON person_attendance (person_id, service_type, service_date);
