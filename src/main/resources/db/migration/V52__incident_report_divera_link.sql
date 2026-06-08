-- Einsatzbericht ↔ DIVERA-Alarm verknüpfen

ALTER TABLE incident_reports
    ADD COLUMN divera_alarm_id BIGINT NULL,
    ADD COLUMN divera_foreign_id VARCHAR(128) NULL,
    ADD UNIQUE KEY uk_ir_unit_divera_alarm (unit_id, divera_alarm_id);
