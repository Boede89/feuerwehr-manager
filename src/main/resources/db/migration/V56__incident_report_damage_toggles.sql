ALTER TABLE incident_reports
    ADD COLUMN person_damages_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER persons_dead_own,
    ADD COLUMN animal_damages_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER animals_dead;

UPDATE incident_reports
SET person_damages_enabled = TRUE
WHERE persons_rescued > 0
   OR persons_injured > 0
   OR persons_recovered > 0
   OR persons_dead > 0
   OR persons_evacuated > 0
   OR persons_injured_own > 0
   OR persons_dead_own > 0;

UPDATE incident_reports
SET animal_damages_enabled = TRUE
WHERE animals_rescued > 0
   OR animals_injured > 0
   OR animals_recovered > 0
   OR animals_dead > 0;
