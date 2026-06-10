ALTER TABLE incident_reports
    ADD COLUMN person_damage_details_json TEXT NULL AFTER person_damages_enabled;

UPDATE incident_reports
SET person_damage_details_json = '{"rescued":[],"injured":[],"recovered":[],"dead":[]}'
WHERE person_damage_details_json IS NULL;
