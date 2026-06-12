ALTER TABLE unit_termine
    ADD COLUMN audience_all BOOLEAN NOT NULL DEFAULT TRUE AFTER instructor_person_id;

CREATE TABLE termin_group_assignments (
    termin_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    PRIMARY KEY (termin_id, group_id),
    CONSTRAINT fk_tga_termin FOREIGN KEY (termin_id) REFERENCES unit_termine(id) ON DELETE CASCADE,
    CONSTRAINT fk_tga_group FOREIGN KEY (group_id) REFERENCES person_groups(id) ON DELETE CASCADE
);
