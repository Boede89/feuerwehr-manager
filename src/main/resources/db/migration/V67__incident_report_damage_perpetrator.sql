ALTER TABLE incident_reports
    ADD COLUMN damage_perpetrator_json TEXT NULL AFTER person_damage_details_json;

UPDATE incident_reports
SET damage_perpetrator_json = '{"name":null,"address":null,"birthdate":null,"licensePlate":null}'
WHERE damage_perpetrator_json IS NULL;
