-- Pflicht-Passwortwechsel nach zugesandtem Initialpasswort
ALTER TABLE users
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
