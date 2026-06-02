-- Rollen: SUPER_ADMIN, UNIT_ADMIN (ehem. ADMIN), USER; Einheitszuordnung für Einheitsadmin/Benutzer

ALTER TABLE users
    ADD COLUMN unit_id BIGINT NULL AFTER role,
    ADD CONSTRAINT fk_users_unit FOREIGN KEY (unit_id) REFERENCES units (id);

UPDATE users SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN';
