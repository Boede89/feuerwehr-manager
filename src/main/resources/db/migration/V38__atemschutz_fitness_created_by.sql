ALTER TABLE atemschutz_fitness_records
    ADD COLUMN created_by_user_id BIGINT NULL,
    ADD CONSTRAINT fk_atemschutz_fitness_created_by
        FOREIGN KEY (created_by_user_id) REFERENCES users (id) ON DELETE SET NULL;
