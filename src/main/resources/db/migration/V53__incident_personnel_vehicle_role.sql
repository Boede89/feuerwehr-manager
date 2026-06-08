-- Einheitsführer / Maschinist pro Fahrzeugbesatzung

ALTER TABLE incident_report_personnel
    ADD COLUMN vehicle_role VARCHAR(24) NULL AFTER source;
