CREATE TABLE course_detail_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    unit_id BIGINT NOT NULL,
    plan_year INT NOT NULL,
    use_participation BOOLEAN NOT NULL DEFAULT FALSE,
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_course_detail_plan_year (unit_id, plan_year, test_data),
    CONSTRAINT fk_cdp_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE TABLE course_detail_plan_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    seats INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cdp_item_course (plan_id, course_id),
    CONSTRAINT fk_cdpi_plan FOREIGN KEY (plan_id) REFERENCES course_detail_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_cdpi_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

CREATE TABLE course_detail_plan_entries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    item_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cdp_entry_person (item_id, person_id),
    CONSTRAINT fk_cdpe_item FOREIGN KEY (item_id) REFERENCES course_detail_plan_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_cdpe_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE
);
