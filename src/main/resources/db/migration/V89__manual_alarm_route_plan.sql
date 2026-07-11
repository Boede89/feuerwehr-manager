-- Geplanter Routen-Startpunkt beim Anlegen/Bearbeiten (vor Einsatzstart)
ALTER TABLE manual_alarms
    ADD COLUMN route_plan_use_geraetehaus BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN route_plan_start_address VARCHAR(512) NULL;
