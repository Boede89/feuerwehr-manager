CREATE TABLE instructor_groups (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    thema VARCHAR(150) NOT NULL,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_instructor_groups_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    CONSTRAINT uq_instructor_groups_unit_thema UNIQUE (unit_id, thema, test_data)
);

CREATE INDEX idx_instructor_groups_unit ON instructor_groups(unit_id);

CREATE TABLE instructor_group_members (
    group_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, person_id),
    CONSTRAINT fk_igm_group FOREIGN KEY (group_id) REFERENCES instructor_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_igm_person FOREIGN KEY (person_id) REFERENCES persons(id) ON DELETE CASCADE
);

CREATE INDEX idx_instructor_group_members_person ON instructor_group_members(person_id);
