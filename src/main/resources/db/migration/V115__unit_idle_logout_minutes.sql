ALTER TABLE units
    ADD COLUMN idle_logout_minutes INT NULL DEFAULT 0;

UPDATE units
SET idle_logout_minutes = 0
WHERE idle_logout_minutes IS NULL;
