ALTER TABLE application_settings
    ADD COLUMN modules_json TEXT NULL;

UPDATE application_settings
SET modules_json = '{"personal":true,"reservierungen":false,"atemschutz":false,"berichte":false,"auswertung":false}'
WHERE id = 1;
