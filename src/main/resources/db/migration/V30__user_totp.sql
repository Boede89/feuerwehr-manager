ALTER TABLE users
    ADD COLUMN totp_secret VARCHAR(512) NULL AFTER divera_api_key,
    ADD COLUMN totp_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER totp_secret;
