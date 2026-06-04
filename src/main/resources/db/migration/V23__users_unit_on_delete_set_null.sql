-- Beim Löschen einer Einheit bleiben Benutzerkonten erhalten; die Zuordnung wird entfernt.
ALTER TABLE users DROP FOREIGN KEY fk_users_unit;
ALTER TABLE users
    ADD CONSTRAINT fk_users_unit FOREIGN KEY (unit_id) REFERENCES units (id) ON DELETE SET NULL;
