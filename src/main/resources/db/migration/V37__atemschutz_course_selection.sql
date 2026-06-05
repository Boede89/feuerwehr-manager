-- Idempotent via Prozedur (Flyway/MySQL: keine Session-Variablen über mehrere Statements).

DELIMITER //
CREATE PROCEDURE ffm_v37_atemschutz_course()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'unit_atemschutz_settings'
          AND COLUMN_NAME = 'agt_course_id'
    ) THEN
        ALTER TABLE unit_atemschutz_settings ADD COLUMN agt_course_id BIGINT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'unit_atemschutz_settings'
          AND CONSTRAINT_NAME = 'fk_unit_atemschutz_course'
          AND CONSTRAINT_TYPE = 'FOREIGN KEY'
    ) THEN
        ALTER TABLE unit_atemschutz_settings
            ADD CONSTRAINT fk_unit_atemschutz_course
            FOREIGN KEY (agt_course_id) REFERENCES courses (id) ON DELETE SET NULL;
    END IF;
END //
DELIMITER ;

CALL ffm_v37_atemschutz_course();
DROP PROCEDURE IF EXISTS ffm_v37_atemschutz_course;

UPDATE unit_atemschutz_settings uas
INNER JOIN courses c
    ON c.unit_id = uas.unit_id
   AND c.test_data = FALSE
   AND LOWER(TRIM(c.name)) = LOWER(TRIM(uas.agt_course_name))
SET uas.agt_course_id = c.id
WHERE uas.agt_course_id IS NULL;
