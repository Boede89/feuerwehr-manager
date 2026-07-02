ALTER TABLE incident_reports
    ADD COLUMN material_damage_entries_json TEXT NULL AFTER equipment_damage;

UPDATE incident_reports
SET material_damage_entries_json = '[]'
WHERE material_damage_entries_json IS NULL;
