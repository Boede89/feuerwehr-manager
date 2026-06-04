ALTER TABLE qualification_types
    ADD COLUMN dienstgrad_role_id BIGINT NULL AFTER name,
    ADD CONSTRAINT fk_qual_dienstgrad_role FOREIGN KEY (dienstgrad_role_id) REFERENCES unit_roles (id) ON DELETE SET NULL;

CREATE INDEX idx_qual_dienstgrad_role ON qualification_types (dienstgrad_role_id);
