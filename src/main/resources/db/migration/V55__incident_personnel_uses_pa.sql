ALTER TABLE incident_report_personnel
    ADD COLUMN uses_pa BOOLEAN NOT NULL DEFAULT FALSE AFTER vehicle_role;
