ALTER TABLE unit_roles
    ADD COLUMN system_role BOOLEAN NOT NULL DEFAULT FALSE AFTER role_type;

CREATE INDEX idx_unit_roles_system ON unit_roles (unit_id, system_role);
