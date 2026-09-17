ALTER TABLE mediathek_folder_acl
    ADD COLUMN qualification_type_id BIGINT NULL AFTER group_id;

ALTER TABLE mediathek_folder_acl
    ADD CONSTRAINT fk_mediathek_acl_qualification
        FOREIGN KEY (qualification_type_id) REFERENCES qualification_types (id) ON DELETE CASCADE;

CREATE INDEX idx_mediathek_acl_qualification ON mediathek_folder_acl (qualification_type_id);
