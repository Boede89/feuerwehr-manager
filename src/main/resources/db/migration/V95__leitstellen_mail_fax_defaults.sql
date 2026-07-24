-- Defaults für FAX-Betreff und Keyword-Listen (nachträglich, V94 unverändert lassen)
ALTER TABLE unit_leitstellen_mail_settings
    MODIFY COLUMN subject_filter VARCHAR(255) NULL DEFAULT 'FAX',
    MODIFY COLUMN depesche_keywords VARCHAR(512) NOT NULL DEFAULT 'depesche,alarmdepesche',
    MODIFY COLUMN abschluss_keywords VARCHAR(512) NOT NULL DEFAULT 'abschluss,abschlussbericht';

UPDATE unit_leitstellen_mail_settings
SET subject_filter = 'FAX'
WHERE subject_filter IS NULL OR TRIM(subject_filter) = '';
