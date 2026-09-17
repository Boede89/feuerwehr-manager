CREATE TABLE mediathek_folders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT NULL,
    owner_unit_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    inherit_acl TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mediathek_folder_parent FOREIGN KEY (parent_id) REFERENCES mediathek_folders (id) ON DELETE CASCADE,
    CONSTRAINT fk_mediathek_folder_owner_unit FOREIGN KEY (owner_unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE INDEX idx_mediathek_folders_parent ON mediathek_folders (parent_id);
CREATE INDEX idx_mediathek_folders_owner ON mediathek_folders (owner_unit_id);

CREATE TABLE mediathek_folder_units (
    folder_id BIGINT NOT NULL,
    unit_id BIGINT NOT NULL,
    PRIMARY KEY (folder_id, unit_id),
    CONSTRAINT fk_mediathek_folder_units_folder FOREIGN KEY (folder_id) REFERENCES mediathek_folders (id) ON DELETE CASCADE,
    CONSTRAINT fk_mediathek_folder_units_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE CASCADE
);

CREATE INDEX idx_mediathek_folder_units_unit ON mediathek_folder_units (unit_id);

CREATE TABLE mediathek_files (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    uploaded_by_user_id BIGINT NULL,
    uploaded_by_display_name VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mediathek_file_folder FOREIGN KEY (folder_id) REFERENCES mediathek_folders (id) ON DELETE CASCADE,
    CONSTRAINT fk_mediathek_file_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_mediathek_files_folder ON mediathek_files (folder_id);

CREATE TABLE mediathek_folder_acl (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    folder_id BIGINT NOT NULL,
    person_id BIGINT NULL,
    group_id BIGINT NULL,
    access_level VARCHAR(16) NOT NULL,
    CONSTRAINT fk_mediathek_acl_folder FOREIGN KEY (folder_id) REFERENCES mediathek_folders (id) ON DELETE CASCADE,
    CONSTRAINT fk_mediathek_acl_person FOREIGN KEY (person_id) REFERENCES persons (id) ON DELETE CASCADE,
    CONSTRAINT fk_mediathek_acl_group FOREIGN KEY (group_id) REFERENCES person_groups (id) ON DELETE CASCADE
);

CREATE INDEX idx_mediathek_acl_folder ON mediathek_folder_acl (folder_id);
CREATE INDEX idx_mediathek_acl_person ON mediathek_folder_acl (person_id);
CREATE INDEX idx_mediathek_acl_group ON mediathek_folder_acl (group_id);
