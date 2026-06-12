ALTER TABLE unit_termine
    ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'dienstplan' AFTER unit_id,
    ADD COLUMN instructor_person_id BIGINT NULL AFTER description;

ALTER TABLE unit_termine
    ADD CONSTRAINT fk_ut_instructor FOREIGN KEY (instructor_person_id) REFERENCES persons(id) ON DELETE SET NULL;

CREATE INDEX idx_unit_termine_unit_category_start ON unit_termine (unit_id, category, start_at);
