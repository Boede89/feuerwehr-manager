-- Halbfertige V72-Tabellen entfernen und fehlgeschlagenen Flyway-Eintrag löschen

DROP TABLE IF EXISTS attendance_report_personnel;
DROP TABLE IF EXISTS attendance_reports;

DELETE FROM flyway_schema_history WHERE version = '72';
