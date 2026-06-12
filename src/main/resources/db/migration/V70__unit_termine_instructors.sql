CREATE TABLE termin_instructor_assignments (
    termin_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    PRIMARY KEY (termin_id, person_id),
    CONSTRAINT fk_tia_termin FOREIGN KEY (termin_id) REFERENCES unit_termine(id) ON DELETE CASCADE,
    CONSTRAINT fk_tia_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

INSERT INTO termin_instructor_assignments (termin_id, person_id)
SELECT id, instructor_person_id
FROM unit_termine
WHERE instructor_person_id IS NOT NULL;

ALTER TABLE unit_termine
    DROP FOREIGN KEY fk_ut_instructor;

ALTER TABLE unit_termine
    DROP COLUMN instructor_person_id;
