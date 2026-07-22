ALTER TABLE attendance_reports
    ADD COLUMN material_damage_entries_json TEXT NULL AFTER deployed_equipment_json,
    ADD COLUMN crew_injury_entries_json TEXT NULL AFTER material_damage_entries_json;
