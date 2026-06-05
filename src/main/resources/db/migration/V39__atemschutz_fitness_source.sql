ALTER TABLE atemschutz_fitness_records
    ADD COLUMN source_label VARCHAR(255) NULL,
    ADD COLUMN source_ref_type VARCHAR(32) NULL,
    ADD COLUMN source_ref_id BIGINT NULL;
