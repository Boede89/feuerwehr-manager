ALTER TABLE courses
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER name;

SET @row := 0;
UPDATE courses c
    JOIN (
        SELECT id, (@row := @row + 1) - 1 AS rn
        FROM courses
        ORDER BY unit_id, name
    ) ordered ON c.id = ordered.id
SET c.sort_order = ordered.rn;
