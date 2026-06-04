-- Einheitseinstellungen: Stammdaten, Rollen, Schnittstellen, Technik

ALTER TABLE units
    ADD COLUMN street VARCHAR(255) NULL AFTER name,
    ADD COLUMN postal_city VARCHAR(255) NULL AFTER street,
    ADD COLUMN logo_base64 MEDIUMTEXT NULL AFTER postal_city;

CREATE TABLE unit_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    role_type VARCHAR(16) NOT NULL DEFAULT 'dienstgrad',
    permissions_json TEXT NOT NULL,
    role_level INT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_unit_roles_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE,
    CONSTRAINT uq_unit_roles_name UNIQUE (unit_id, name)
);

ALTER TABLE users
    ADD COLUMN organizational_role_id BIGINT NULL AFTER unit_id,
    ADD CONSTRAINT fk_users_org_role FOREIGN KEY (organizational_role_id) REFERENCES unit_roles(id) ON DELETE SET NULL;

CREATE TABLE user_unit_functions (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_uuf_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_uuf_role FOREIGN KEY (role_id) REFERENCES unit_roles(id) ON DELETE CASCADE
);

CREATE TABLE unit_smtp_settings (
    unit_id BIGINT PRIMARY KEY,
    smtp_host VARCHAR(255) NULL,
    smtp_port INT NULL,
    smtp_username VARCHAR(255) NULL,
    smtp_password VARCHAR(512) NULL,
    smtp_from_email VARCHAR(255) NULL,
    smtp_from_name VARCHAR(255) NULL,
    smtp_encryption VARCHAR(16) NULL DEFAULT 'TLS',
    CONSTRAINT fk_unit_smtp_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
);

CREATE TABLE unit_calendar_settings (
    unit_id BIGINT PRIMARY KEY,
    provider VARCHAR(32) NULL DEFAULT 'google',
    calendar_url VARCHAR(1024) NULL,
    calendar_id VARCHAR(512) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT fk_unit_calendar_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
);

CREATE TABLE vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    test_data TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicles_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
);

CREATE TABLE rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    unit_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    test_data TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_rooms_unit FOREIGN KEY (unit_id) REFERENCES units(id) ON DELETE CASCADE
);

CREATE TABLE vehicle_equipment_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_vec_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE
);

CREATE TABLE vehicle_equipment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_ve_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    CONSTRAINT fk_ve_category FOREIGN KEY (category_id) REFERENCES vehicle_equipment_categories(id) ON DELETE SET NULL
);

CREATE INDEX idx_unit_roles_unit ON unit_roles(unit_id);
CREATE INDEX idx_vehicles_unit ON vehicles(unit_id);
CREATE INDEX idx_rooms_unit ON rooms(unit_id);
