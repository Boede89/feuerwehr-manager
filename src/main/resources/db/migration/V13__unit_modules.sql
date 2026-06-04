-- Module je Einheit (nicht mehr global)

ALTER TABLE units
    ADD COLUMN modules_json TEXT NULL;

UPDATE units
SET modules_json = COALESCE(
    (SELECT modules_json FROM application_settings WHERE id = 1 LIMIT 1),
    '{"personal":true,"reservierungen":false,"atemschutz":false,"berichte":false,"auswertung":false}'
)
WHERE modules_json IS NULL;
