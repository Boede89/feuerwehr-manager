-- CSA-Benachrichtigungsvorlagen für bestehende Einheiten nachziehen.
-- V106 hat CSA-Spalten ergänzt, aber keine E-Mail-Vorlagen angelegt.
-- Ohne diese Keys schlägt die Atemschutz-Einstellungsseite mit 500 fehl.

INSERT INTO atemschutz_email_templates (unit_id, template_key, template_name, subject, body)
SELECT
    s.unit_id,
    'csa_warnung',
    'CSA – Erinnerung (Gelb)',
    'Erinnerung: CSA-Übung/Einsatz läuft bald ab',
    CONCAT(
        'Hallo {first_name} {last_name},', CHAR(10), CHAR(10),
        'Ihre CSA-Übung/Ihr CSA-Einsatz läuft am {expiry_date} ab.', CHAR(10), CHAR(10),
        'Bitte Teilnahme unter CSA.', CHAR(10), CHAR(10),
        'Mit freundlichen Grüßen', CHAR(10),
        'Ihre Feuerwehr'
    )
FROM unit_atemschutz_settings s
WHERE NOT EXISTS (
    SELECT 1
    FROM atemschutz_email_templates t
    WHERE t.unit_id = s.unit_id
      AND t.template_key = 'csa_warnung'
);

INSERT INTO atemschutz_email_templates (unit_id, template_key, template_name, subject, body)
SELECT
    s.unit_id,
    'csa_abgelaufen',
    'CSA – Aufforderung (Rot)',
    'ACHTUNG: CSA-Übung/Einsatz ist abgelaufen',
    CONCAT(
        'Hallo {first_name} {last_name},', CHAR(10), CHAR(10),
        'Ihre CSA-Tauglichkeit ist seit dem {expiry_date} abgelaufen!', CHAR(10), CHAR(10),
        'Bitte SOFORT CSA-Übung oder -Einsatz.', CHAR(10), CHAR(10),
        'Mit freundlichen Grüßen', CHAR(10),
        'Ihre Feuerwehr'
    )
FROM unit_atemschutz_settings s
WHERE NOT EXISTS (
    SELECT 1
    FROM atemschutz_email_templates t
    WHERE t.unit_id = s.unit_id
      AND t.template_key = 'csa_abgelaufen'
);
