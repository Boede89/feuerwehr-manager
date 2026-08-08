-- Nur fehlgeschlagenen V102-Eintrag entfernen, damit Flyway die idempotente Migration erneut ausführt.
DELETE FROM flyway_schema_history WHERE version = '102' AND success = 0;
