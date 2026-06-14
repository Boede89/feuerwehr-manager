ALTER TABLE equipment_maintenance_reports
    ADD COLUMN event_art VARCHAR(50) NULL AFTER typ,
    ADD COLUMN vehicles_data_json TEXT NULL AFTER deployed_equipment_json;

UPDATE equipment_maintenance_reports
SET vehicles_data_json = deployed_equipment_json
WHERE vehicles_data_json IS NULL
  AND deployed_equipment_json IS NOT NULL
  AND deployed_equipment_json <> '[]';

UPDATE equipment_maintenance_reports
SET event_art = 'BRANDEINSATZ'
WHERE event_art IS NULL;
