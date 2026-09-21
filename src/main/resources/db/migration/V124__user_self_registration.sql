-- Self-registration: Stammdaten am Benutzer + Freigabe-Status + Schalter pro Einheit
ALTER TABLE users
    ADD COLUMN first_name VARCHAR(100) NULL,
    ADD COLUMN last_name VARCHAR(100) NULL,
    ADD COLUMN birthdate DATE NULL,
    ADD COLUMN registration_pending BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_users_registration_pending_unit
    ON users (unit_id, registration_pending, active);

ALTER TABLE units
    ADD COLUMN self_registration_enabled BOOLEAN NOT NULL DEFAULT FALSE;
