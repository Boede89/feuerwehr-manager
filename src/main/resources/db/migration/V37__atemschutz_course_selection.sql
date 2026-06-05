-- Idempotent: toleriert Abbruch bei vorherigem fehlgeschlagenem Lauf (Flyway repair + Neustart).

SET @db := DATABASE();

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'unit_atemschutz_settings'
      AND COLUMN_NAME = 'agt_course_id'
);
SET @sql := IF(
    @col_exists = 0,
    'ALTER TABLE unit_atemschutz_settings ADD COLUMN agt_course_id BIGINT NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_exists := (
    SELECT COUNT(*)
    FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA = @db
      AND TABLE_NAME = 'unit_atemschutz_settings'
      AND CONSTRAINT_NAME = 'fk_unit_atemschutz_course'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @sql := IF(
    @fk_exists = 0,
    'ALTER TABLE unit_atemschutz_settings ADD CONSTRAINT fk_unit_atemschutz_course FOREIGN KEY (agt_course_id) REFERENCES courses (id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE unit_atemschutz_settings uas
INNER JOIN courses c
    ON c.unit_id = uas.unit_id
   AND c.test_data = FALSE
   AND LOWER(TRIM(c.name)) = LOWER(TRIM(uas.agt_course_name))
SET uas.agt_course_id = c.id
WHERE uas.agt_course_id IS NULL;
