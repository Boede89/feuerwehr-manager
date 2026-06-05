CREATE TABLE IF NOT EXISTS unit_atemschutz_settings (
    unit_id BIGINT NOT NULL PRIMARY KEY,
    warn_days INT NOT NULL DEFAULT 90,
    agt_course_name VARCHAR(64) NOT NULL DEFAULT 'AGT',
    notification_user_ids TEXT,
    cc_user_ids TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_atemschutz_settings_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

ALTER TABLE unit_atemschutz_settings ADD COLUMN agt_course_id BIGINT NULL;

ALTER TABLE unit_atemschutz_settings
    ADD CONSTRAINT fk_unit_atemschutz_course
    FOREIGN KEY (agt_course_id) REFERENCES courses (id) ON DELETE SET NULL;

UPDATE unit_atemschutz_settings uas
INNER JOIN courses c
    ON c.unit_id = uas.unit_id
   AND c.test_data = FALSE
   AND LOWER(TRIM(c.name)) = LOWER(TRIM(uas.agt_course_name))
SET uas.agt_course_id = c.id
WHERE uas.agt_course_id IS NULL;
