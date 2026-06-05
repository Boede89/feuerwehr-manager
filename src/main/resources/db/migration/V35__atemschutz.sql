CREATE TABLE atemschutz_carriers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    notes VARCHAR(512),
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    production_source_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_atemschutz_carrier_unit FOREIGN KEY (unit_id) REFERENCES units (id),
    CONSTRAINT fk_atemschutz_carrier_person FOREIGN KEY (person_id) REFERENCES persons (id),
    CONSTRAINT uq_atemschutz_carrier_person UNIQUE (person_id)
);

CREATE INDEX idx_atemschutz_carriers_unit ON atemschutz_carriers (unit_id);

CREATE TABLE atemschutz_fitness_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    carrier_id BIGINT NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    valid_from DATE,
    valid_until DATE NOT NULL,
    physician VARCHAR(255),
    result_notes VARCHAR(1024),
    test_data BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_atemschutz_fitness_carrier FOREIGN KEY (carrier_id) REFERENCES atemschutz_carriers (id) ON DELETE CASCADE
);

CREATE INDEX idx_atemschutz_fitness_carrier ON atemschutz_fitness_records (carrier_id);
CREATE INDEX idx_atemschutz_fitness_until ON atemschutz_fitness_records (valid_until);
