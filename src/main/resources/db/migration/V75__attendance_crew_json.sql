ALTER TABLE attendance_reports
    ADD COLUMN crew_assignments_json TEXT NULL AFTER objekt,
    ADD COLUMN deployed_equipment_json TEXT NULL AFTER crew_assignments_json;
