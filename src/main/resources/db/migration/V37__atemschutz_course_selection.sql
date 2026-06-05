ALTER TABLE unit_atemschutz_settings
    ADD COLUMN agt_course_id BIGINT NULL,
    ADD CONSTRAINT fk_unit_atemschutz_course
        FOREIGN KEY (agt_course_id) REFERENCES courses (id) ON DELETE SET NULL;

UPDATE unit_atemschutz_settings uas
INNER JOIN courses c
    ON c.unit_id = uas.unit_id
   AND c.test_data = FALSE
   AND LOWER(TRIM(c.name)) = LOWER(TRIM(uas.agt_course_name))
SET uas.agt_course_id = c.id;
