-- Testmodus: Schattenkopien von Produktivdaten (Änderungen ohne Wirkung auf Produktiv)

ALTER TABLE persons
    ADD COLUMN production_source_id BIGINT NULL AFTER test_data,
    ADD CONSTRAINT fk_person_production_source FOREIGN KEY (production_source_id) REFERENCES persons (id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_person_production_shadow (production_source_id);

ALTER TABLE qualification_types
    ADD COLUMN production_source_id BIGINT NULL AFTER test_data,
    ADD CONSTRAINT fk_qual_production_source FOREIGN KEY (production_source_id) REFERENCES qualification_types (id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_qual_production_shadow (production_source_id);

ALTER TABLE courses
    ADD COLUMN production_source_id BIGINT NULL AFTER test_data,
    ADD CONSTRAINT fk_course_production_source FOREIGN KEY (production_source_id) REFERENCES courses (id) ON DELETE CASCADE,
    ADD UNIQUE KEY uk_course_production_shadow (production_source_id);
