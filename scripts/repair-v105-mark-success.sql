-- Fehlgeschlagenen V105-Eintrag entfernen, damit Flyway die idempotente Migration erneut ausführt.
DELETE FROM flyway_schema_history WHERE version = '105' AND success = 0;
