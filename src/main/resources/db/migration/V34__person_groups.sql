-- Personal-Gruppen (einheitsbezogen, Personen können mehreren Gruppen angehören)

CREATE TABLE person_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_person_groups_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    CONSTRAINT uq_person_groups_unit_name UNIQUE (unit_id, name, test_data)
);

CREATE INDEX idx_person_groups_unit ON person_groups(unit_id);

CREATE TABLE person_group_members (
    group_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, person_id),
    CONSTRAINT fk_pgm_group FOREIGN KEY (group_id) REFERENCES person_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_pgm_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX idx_person_group_members_person ON person_group_members(person_id);
